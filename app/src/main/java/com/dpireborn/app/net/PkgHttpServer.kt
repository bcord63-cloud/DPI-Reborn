package com.dpireborn.app.net

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Servidor HTTP mínimo que expõe os PKG escolhidos para o PS4 baixar pela rede local.
 * Suporta HEAD, GET, "Range: bytes=..." e várias conexões simultâneas (o instalador do PS4
 * faz HEAD e leitura parcial do cabeçalho antes do download completo).
 *
 * URL servida: http://IP_DO_CELULAR:PORTA/<id>/<nome-do-arquivo>.pkg
 */
class PkgHttpServer(
    private val resolver: ContentResolver,
    private val onServed: (id: String, position: Long, count: Int) -> Unit,
) {
    data class Entry(val id: String, val uri: Uri, val name: String, val size: Long)

    private class Request(val method: String, val path: String, val headers: Map<String, String>)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var server: ServerSocket? = null

    var port: Int = 0
        private set

    fun register(entry: Entry) {
        entries[entry.id] = entry
    }

    fun unregister(id: String) {
        entries.remove(id)
    }

    /** Tenta a porta preferida; se estiver ocupada, usa qualquer porta livre. */
    @Synchronized
    fun start(preferredPort: Int) {
        if (server != null) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        try {
            ss.bind(InetSocketAddress(preferredPort))
        } catch (e: IOException) {
            ss.bind(InetSocketAddress(0))
        }
        server = ss
        port = ss.localPort
        pool.execute { acceptLoop(ss) }
    }

    @Synchronized
    fun stop() {
        try {
            server?.close()
        } catch (e: Exception) {
        }
        server = null
        entries.clear()
        pool.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            try {
                val s = ss.accept()
                pool.execute { handle(s) }
            } catch (e: IOException) {
                if (ss.isClosed) break
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 30_000
            sock.tcpNoDelay = true
            val input = BufferedInputStream(sock.getInputStream(), 8192)
            val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
            while (true) {
                val req = readRequest(input) ?: break
                val keepAlive = respond(req, out)
                out.flush()
                if (!keepAlive) break
            }
        } catch (e: Exception) {
            // cliente desconectou ou timeout
        } finally {
            try {
                sock.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = i.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 16384) return null
        }
        return sb.toString()
    }

    private fun readRequest(i: InputStream): Request? {
        var first = readLine(i) ?: return null
        while (first.isEmpty()) first = readLine(i) ?: return null
        val parts = first.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(i) ?: return null
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return Request(parts[0].uppercase(), parts[1], headers)
    }

    private fun writeHead(out: OutputStream, code: Int, text: String, headers: List<Pair<String, String>>) {
        val sb = StringBuilder("HTTP/1.1 $code $text\r\n")
        for ((k, v) in headers) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun simple(out: OutputStream, code: Int, text: String, keep: Boolean, extra: List<Pair<String, String>> = emptyList()) {
        writeHead(
            out, code, text,
            listOf("Content-Length" to "0", "Connection" to (if (keep) "keep-alive" else "close")) + extra,
        )
    }

    /** Responde uma requisição. Retorna true se a conexão pode continuar aberta. */
    private fun respond(req: Request, out: OutputStream): Boolean {
        val keep = !req.headers["connection"].equals("close", ignoreCase = true)
        if (req.method != "GET" && req.method != "HEAD") {
            simple(out, 405, "Method Not Allowed", false)
            return false
        }
        val rawPath = req.path.substringBefore('?')
        val path = try {
            URLDecoder.decode(rawPath, "UTF-8")
        } catch (e: Exception) {
            rawPath
        }
        val id = path.trim('/').split('/').firstOrNull() ?: ""
        val entry = entries[id]
        if (entry == null) {
            simple(out, 404, "Not Found", keep)
            return keep
        }

        val size = entry.size
        var start = 0L
        var end = size - 1
        var partial = false
        val range = req.headers["range"]
        if (range != null && range.startsWith("bytes=") && size > 0) {
            val spec = range.substring(6).substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val a = spec.substring(0, dash).trim()
                val b = spec.substring(dash + 1).trim()
                try {
                    if (a.isEmpty()) {
                        val n = b.toLong()
                        start = maxOf(0L, size - n)
                        end = size - 1
                    } else {
                        start = a.toLong()
                        end = if (b.isEmpty()) size - 1 else minOf(b.toLong(), size - 1)
                    }
                    partial = true
                } catch (e: NumberFormatException) {
                    partial = false
                    start = 0L
                    end = size - 1
                }
            }
            if (partial && (start > end || start >= size)) {
                simple(out, 416, "Range Not Satisfiable", keep, listOf("Content-Range" to "bytes */$size"))
                return keep
            }
        }

        val length = if (size <= 0L) 0L else end - start + 1
        val headers = ArrayList<Pair<String, String>>()
        headers.add("Content-Type" to "application/octet-stream")
        headers.add("Accept-Ranges" to "bytes")
        headers.add("Content-Length" to length.toString())
        if (partial) headers.add("Content-Range" to "bytes $start-$end/$size")
        headers.add("Connection" to (if (keep) "keep-alive" else "close"))
        if (partial) writeHead(out, 206, "Partial Content", headers) else writeHead(out, 200, "OK", headers)

        if (req.method == "GET" && length > 0) {
            out.flush()
            streamFile(entry, start, length, out)
        }
        return keep
    }

    private fun streamFile(entry: Entry, start: Long, length: Long, out: OutputStream) {
        val pfd = resolver.openFileDescriptor(entry.uri, "r") ?: throw IOException("Não foi possível abrir o arquivo")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            val ch = input.channel
            val buf = ByteArray(256 * 1024)
            var pos = start
            var remaining = length
            while (remaining > 0) {
                val want = minOf(buf.size.toLong(), remaining).toInt()
                val n = ch.read(ByteBuffer.wrap(buf, 0, want), pos)
                if (n <= 0) throw IOException("Fim inesperado do arquivo")
                out.write(buf, 0, n)
                onServed(entry.id, pos, n)
                pos += n
                remaining -= n
            }
            out.flush()
        }
    }
}
