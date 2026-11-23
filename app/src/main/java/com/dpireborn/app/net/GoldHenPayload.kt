package com.dpireborn.app.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Método "payload GoldHEN": o mesmo usado pelo DirectPackageInstaller quando o PS4 não tem o app
 * Remote Package Installer. Protocolo lido do código fonte do DPI (PayloadService.cs, Payload/main.c
 * e Payload/info.c):
 *
 *  1. O celular abre um servidor TCP (porta livre) no IP da rede local.
 *  2. O payload (assets/dpi_payload.bin) tem 6 bytes 0xB4 que são trocados por IP (4 bytes) + porta
 *     (2 bytes, big-endian) do celular, e é enviado ao servidor de payloads do GoldHEN (portas
 *     9090, 9021 ou 9020 do PS4).
 *  3. O payload roda no PS4, conecta de volta no celular e lê um "pacote de comando":
 *       uint32 comando (1 = novo pacote, 0 = encerrar)
 *       string url | string nome | string contentId | string tipo   (cada uma: uint32 tamanho + bytes UTF-8)
 *       uint64 tamanho do PKG
 *       uint32 tamanho do ícone (0 = sem ícone) + bytes PNG
 *     Tudo little-endian. A "url" é a do manifesto JSON servido pelo celular.
 *  4. O payload registra o download no serviço BGFT do PS4 e volta a conectar para o próximo comando.
 */
object GoldHenPayload {
    private val EXPLOIT_PORTS = intArrayOf(9090, 9021, 9020)

    // Limites do buffer do payload (info.c): url 0x800, nome 0x259, id 0x30, tipo 0x10
    private const val MAX_URL = 0x7FF
    private const val MAX_NAME_BYTES = 200
    private const val MAX_ID = 0x2F
    private const val MAX_TYPE = 0x0F

    class Meta(
        val url: String,
        val name: String,
        val contentId: String,
        val type: String,
        val size: Long,
        val icon: ByteArray?,
    )

    private fun indexOfMarker(data: ByteArray): Int {
        val m = 0xB4.toByte()
        var run = 0
        for (i in data.indices) {
            if (data[i] == m) {
                run++
                if (run == 6) return i - 5
            } else {
                run = 0
            }
        }
        return -1
    }

    private fun cut(s: String, maxBytes: Int): String {
        var out = s
        while (out.toByteArray(Charsets.UTF_8).size > maxBytes && out.isNotEmpty()) {
            out = out.dropLast(1)
        }
        return out
    }

    /** Monta o pacote de comando "novo pacote". */
    fun buildCommand(m: Meta): ByteArray {
        if (m.url.toByteArray(Charsets.UTF_8).size > MAX_URL) {
            throw IOException("URL do manifesto grande demais para o payload")
        }
        val bb = ByteArrayOutputStream()

        fun u32(v: Int) {
            bb.write(v and 0xFF)
            bb.write((v shr 8) and 0xFF)
            bb.write((v shr 16) and 0xFF)
            bb.write((v shr 24) and 0xFF)
        }

        fun u64(v: Long) {
            for (i in 0 until 8) bb.write(((v shr (8 * i)) and 0xFF).toInt())
        }

        fun str(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            u32(b.size)
            bb.write(b, 0, b.size)
        }

        u32(1)
        str(m.url)
        str(cut(m.name, MAX_NAME_BYTES))
        str(cut(m.contentId, MAX_ID))
        str(cut(m.type, MAX_TYPE))
        u64(m.size)
        val icon = m.icon
        if (icon == null || icon.isEmpty()) {
            u32(0)
        } else {
            u32(icon.size)
            bb.write(icon, 0, icon.size)
        }
        return bb.toByteArray()
    }

    /**
     * Uma sessão por console: injeta o payload uma vez e entrega pacotes pela conexão reversa.
     * Todas as chamadas são bloqueantes; chame de uma thread de I/O.
     */
    class Session(
        private val ps4Ip: String,
        private val localIp: String,
        private val payloadTemplate: ByteArray,
    ) {
        private var listener: ServerSocket? = null
        private var injected = false

        @Synchronized
        fun send(meta: Meta) {
            val command = buildCommand(meta)
            ensureListener()
            var reinjected = false
            if (!injected) {
                inject()
                injected = true
                reinjected = true
            }
            while (true) {
                val sock: Socket = try {
                    accept(if (reinjected) 20_000 else 10_000)
                } catch (e: SocketTimeoutException) {
                    if (reinjected) {
                        throw IOException(
                            "O payload não conectou de volta ao celular. Veja se o PS4 mostrou uma " +
                                "notificação começando com \"DPI:\" e confira se celular e PS4 estão na mesma rede."
                        )
                    }
                    inject()
                    reinjected = true
                    continue
                }
                var delivered = false
                try {
                    deliver(sock, command)
                    delivered = true
                } catch (e: IOException) {
                    if (reinjected) throw e
                } finally {
                    try {
                        sock.close()
                    } catch (e: Exception) {
                    }
                }
                if (delivered) return
                inject()
                reinjected = true
            }
        }

        /** Pede ao payload que encerre (comando 0) e fecha o servidor local. */
        @Synchronized
        fun finish() {
            val l = listener
            try {
                if (l != null && injected) {
                    l.soTimeout = 2_000
                    try {
                        val s = l.accept()
                        s.getOutputStream().write(byteArrayOf(0, 0, 0, 0))
                        s.getOutputStream().flush()
                        s.close()
                    } catch (e: Exception) {
                        // o payload já pode ter terminado sozinho
                    }
                }
            } finally {
                try {
                    l?.close()
                } catch (e: Exception) {
                }
                listener = null
                injected = false
            }
        }

        private fun ensureListener() {
            if (listener != null) return
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName(localIp), 0))
            listener = ss
        }

        private fun accept(timeoutMs: Int): Socket {
            val l = listener ?: throw IOException("Servidor local não iniciado")
            l.soTimeout = timeoutMs
            val s = l.accept()
            s.tcpNoDelay = true
            return s
        }

        private fun deliver(sock: Socket, command: ByteArray) {
            val out = sock.getOutputStream()
            out.write(command)
            out.flush()
            sock.shutdownOutput()
            // O payload fecha a conexão depois de ler tudo; esperamos isso para ter certeza da entrega.
            sock.soTimeout = 8_000
            try {
                val input = sock.getInputStream()
                while (input.read() != -1) {
                    // descarta
                }
            } catch (e: SocketTimeoutException) {
                // sem fechamento a tempo: segue assim mesmo
            }
        }

        private fun inject() {
            val l = listener ?: throw IOException("Servidor local não iniciado")
            val data = payloadTemplate.copyOf()
            val off = indexOfMarker(data)
            if (off < 0) throw IOException("Payload embutido inválido (marcador não encontrado)")
            val ip = InetAddress.getByName(localIp).address
            if (ip.size != 4) throw IOException("Só IPv4 é suportado")
            val port = l.localPort
            System.arraycopy(ip, 0, data, off, 4)
            data[off + 4] = ((port shr 8) and 0xFF).toByte()
            data[off + 5] = (port and 0xFF).toByte()

            var last: Exception? = null
            for (round in 0 until 2) {
                for (p in EXPLOIT_PORTS) {
                    try {
                        Socket().use { s ->
                            s.tcpNoDelay = true
                            s.connect(InetSocketAddress(ps4Ip, p), 3_000)
                            val out = s.getOutputStream()
                            out.write(data)
                            out.flush()
                            s.shutdownOutput()
                        }
                        return
                    } catch (e: Exception) {
                        last = e
                    }
                }
                if (round == 0) Thread.sleep(1_500)
            }
            throw IOException(
                "Não consegui enviar o payload ao PS4 (portas 9090, 9021 e 9020). Ative o servidor de " +
                    "payloads do GoldHEN no console. Detalhe: ${last?.message ?: "sem resposta"}"
            )
        }
    }
}
