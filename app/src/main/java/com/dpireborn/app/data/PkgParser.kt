package com.dpireborn.app.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Leitor de metadados de PKG do PS4.
 *
 * Layout confirmado em fontes públicas (orbis-pkg / ps4-pkg-info / psdevwiki):
 *  - Cabeçalho big-endian: magic 0x7F434E54 em 0x00, quantidade de entradas em 0x10,
 *    offset da tabela de entradas em 0x18, content_id (36 bytes) em 0x40.
 *  - Cada entrada tem 32 bytes: id, filename_offset, flags1, flags2, offset, size, padding.
 *  - Entrada 0x1000 = param.sfo, entrada 0x1200 = icon0.png. Bit 31 de flags1 = criptografada.
 *  - PARAM.SFO é little-endian: magic "\0PSF", tabela de chaves em 0x08, de dados em 0x0C,
 *    quantidade em 0x10, índices de 16 bytes a partir de 0x14.
 */
object PkgParser {
    private const val MAGIC = 0x7F434E54
    private const val ID_PARAM_SFO = 0x1000
    private const val ID_ICON0 = 0x1200
    private const val SFO_MAGIC = 0x46535000 // "\0PSF" lido como little-endian

    fun parse(ctx: Context, uri: Uri, iconDir: File, iconName: String): PkgInfo {
        return try {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                ?: return PkgInfo(recognized = false)
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                parseChannel(input.channel, iconDir, iconName)
            }
        } catch (e: Exception) {
            PkgInfo(recognized = false)
        }
    }

    private const val ZERO_DIGEST = "0000000000000000000000000000000000000000000000000000000000000000"

    /**
     * Digest do cabeçalho (32 bytes em 0xFE0, "pkg_digest" na documentação do formato), em hexadecimal
     * maiúsculo. É o valor que o DPI coloca em "packageDigest" no manifesto JSON.
     */
    fun readHeaderDigest(ctx: Context, uri: Uri): String {
        return try {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return ZERO_DIGEST
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                val d = readAt(input.channel, 0xFE0L, 32)
                d?.joinToString("") { "%02X".format(it) } ?: ZERO_DIGEST
            }
        } catch (e: Exception) {
            ZERO_DIGEST
        }
    }

    private fun parseChannel(ch: FileChannel, iconDir: File, iconName: String): PkgInfo {
        val head = readAt(ch, 0L, 0x80) ?: return PkgInfo(recognized = false)
        val hb = ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN)
        if (hb.getInt(0) != MAGIC) return PkgInfo(recognized = false)

        val entryCount = hb.getInt(0x10)
        val tableOffset = hb.getInt(0x18).toLong() and 0xFFFFFFFFL
        val contentId = String(head, 0x40, 36, Charsets.ISO_8859_1)
            .trim { it == '\u0000' || it == ' ' }

        if (entryCount <= 0 || entryCount > 4096) {
            return PkgInfo(recognized = true, contentId = contentId)
        }
        val table = readAt(ch, tableOffset, entryCount * 32)
            ?: return PkgInfo(recognized = true, contentId = contentId)

        val tb = ByteBuffer.wrap(table).order(ByteOrder.BIG_ENDIAN)
        var sfoBytes: ByteArray? = null
        var iconBytes: ByteArray? = null
        for (i in 0 until entryCount) {
            val base = i * 32
            val id = tb.getInt(base)
            val flags1 = tb.getInt(base + 8)
            val off = tb.getInt(base + 16).toLong() and 0xFFFFFFFFL
            val size = tb.getInt(base + 20).toLong() and 0xFFFFFFFFL
            val encrypted = flags1 < 0 // bit 31
            if (encrypted || size <= 0L) continue
            if (id == ID_PARAM_SFO && size <= 1_048_576L) {
                sfoBytes = readAt(ch, off, size.toInt())
            } else if (id == ID_ICON0 && size <= 4_194_304L) {
                iconBytes = readAt(ch, off, size.toInt())
            }
        }

        var iconPath: String? = null
        val icon = iconBytes
        if (icon != null && icon.size > 8 && icon[0] == 0x89.toByte() && icon[1] == 'P'.code.toByte()) {
            try {
                iconDir.mkdirs()
                val f = File(iconDir, "$iconName.png")
                f.writeBytes(icon)
                iconPath = f.absolutePath
            } catch (e: Exception) {
                iconPath = null
            }
        }

        val sfo = sfoBytes?.let { parseSfo(it) } ?: emptyMap()
        return PkgInfo(
            recognized = true,
            title = sfo["TITLE"]?.takeIf { it.isNotBlank() },
            titleId = sfo["TITLE_ID"]?.takeIf { it.isNotBlank() },
            contentId = (sfo["CONTENT_ID"]?.takeIf { it.isNotBlank() }) ?: contentId.takeIf { it.isNotBlank() },
            appVersion = (sfo["APP_VER"]?.takeIf { it.isNotBlank() }) ?: sfo["VERSION"]?.takeIf { it.isNotBlank() },
            category = sfo["CATEGORY"]?.takeIf { it.isNotBlank() },
            iconPath = iconPath,
        )
    }

    private fun parseSfo(b: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            if (b.size < 20) return out
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.getInt(0) != SFO_MAGIC) return out
            val keyStart = bb.getInt(8)
            val dataStart = bb.getInt(12)
            val count = bb.getInt(16)
            if (count < 0 || count > 512) return out
            for (i in 0 until count) {
                val base = 20 + i * 16
                if (base + 16 > b.size) break
                val keyOff = bb.getShort(base).toInt() and 0xFFFF
                val type = b[base + 3].toInt() and 0xFF
                val len = bb.getInt(base + 4)
                val dataOff = bb.getInt(base + 12)

                var kp = keyStart + keyOff
                val kb = StringBuilder()
                while (kp in b.indices && b[kp].toInt() != 0) {
                    kb.append((b[kp].toInt() and 0xFF).toChar())
                    kp++
                }
                val dp = dataStart + dataOff
                if (dp < 0 || dp >= b.size) continue
                val value = if (type == 4) {
                    if (dp + 4 <= b.size) bb.getInt(dp).toString() else ""
                } else {
                    var end = dp
                    val limit = minOf(b.size, dp + maxOf(len, 0))
                    while (end < limit && b[end].toInt() != 0) end++
                    String(b, dp, end - dp, Charsets.UTF_8)
                }
                out[kb.toString()] = value
            }
        } catch (e: Exception) {
            // SFO malformado: devolve o que foi lido
        }
        return out
    }

    private fun readAt(ch: FileChannel, pos: Long, len: Int): ByteArray? {
        if (len < 0 || len > 8 * 1024 * 1024) return null
        val buf = ByteBuffer.allocate(len)
        var p = pos
        while (buf.hasRemaining()) {
            val n = ch.read(buf, p)
            if (n <= 0) break
            p += n
        }
        return if (buf.hasRemaining()) null else buf.array()
    }
}
