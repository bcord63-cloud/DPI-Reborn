package com.dpireborn.app.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.dpireborn.app.data.FileItem
import com.dpireborn.app.data.PkgInfo
import com.dpireborn.app.data.PkgParser
import com.dpireborn.app.data.PsConsole
import com.dpireborn.app.data.Store
import com.dpireborn.app.data.TransferRecord
import com.dpireborn.app.data.TransferStatus
import com.dpireborn.app.net.GoldHenPayload
import com.dpireborn.app.net.NetUtil
import com.dpireborn.app.net.PkgHttpServer
import com.dpireborn.app.net.Ps4Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Orquestra os envios: sobe o servidor HTTP local, avisa o PS4 (pela API do Remote Package Installer
 * ou pelo payload do GoldHEN, como o DirectPackageInstaller) e acompanha quantos bytes distintos
 * o PS4 já leu do celular.
 *
 * O progresso mostrado é o do arquivo servido ao PS4 (dados reais), não uma estimativa
 * da instalação no console.
 */
object TransferManager {
    private const val FOUR_GIB = 4294967296L
    private const val NO_DOWNLOAD_TIMEOUT_MS = 120_000L

    private enum class Method { RPI, PAYLOAD }

    private var appCtx: Context? = null
    private var store: Store? = null
    private var server: PkgHttpServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val trackers = ConcurrentHashMap<String, Tracker>()
    private val taskIds = ConcurrentHashMap<String, Long>()
    private val consoleOf = ConcurrentHashMap<String, PsConsole>()
    private var stopJob: Job? = null

    // Sessões do payload GoldHEN: uma por console, compartilhada pelos envios simultâneos.
    private val payloadLock = Any()
    private val sessions = HashMap<String, GoldHenPayload.Session>()
    private val sessionUsers = HashMap<String, Int>()
    private val payloadKey = ConcurrentHashMap<String, String>()

    val active = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val serverUrl = MutableStateFlow<String?>(null)

    /** Mede bytes enviados e cobertura (faixas distintas) de um arquivo. */
    private class Tracker(val size: Long) {
        private val ranges = ArrayList<LongArray>()
        val total = AtomicLong(0L)

        @Synchronized
        fun add(start: Long, len: Long) {
            total.addAndGet(len)
            var s = start
            var e = start + len
            val it = ranges.iterator()
            while (it.hasNext()) {
                val r = it.next()
                if (r[1] >= s && r[0] <= e) {
                    s = minOf(s, r[0])
                    e = maxOf(e, r[1])
                    it.remove()
                }
            }
            ranges.add(longArrayOf(s, e))
        }

        @Synchronized
        fun covered(): Long {
            var sum = 0L
            for (r in ranges) sum += r[1] - r[0]
            return minOf(sum, size)
        }
    }

    @Synchronized
    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val s = Store(ctx.applicationContext)
        store = s
        history.value = s.loadHistory()
    }

    fun enqueue(
        ctx: Context,
        items: List<FileItem>,
        console: PsConsole,
        preferredPort: Int,
        infos: Map<String, PkgInfo>,
    ) {
        init(ctx)
        for (item in items) {
            val id = UUID.randomUUID().toString().replace("-", "").take(8)
            val rec = TransferRecord(
                id = id,
                fileName = item.name,
                fileUri = item.uri,
                size = item.size,
                consoleName = console.name,
                consoleIp = console.ip,
                status = TransferStatus.QUEUED,
                covered = 0L,
                speedBps = 0L,
                startedAt = System.currentTimeMillis(),
                endedAt = 0L,
                avgSpeedBps = 0L,
                detail = "Na fila",
            )
            consoleOf[id] = console
            active.update { it + rec }
            val info = infos[item.uri]
            jobs[id] = scope.launch { runTransfer(rec, console, preferredPort, item, info) }
        }
        ensureService()
    }

    fun cancel(id: String) {
        val c = consoleOf[id]
        val tid = taskIds[id]
        jobs[id]?.cancel()
        if (c != null && tid != null) {
            scope.launch {
                try {
                    Ps4Client.taskCommand(c.ip, c.port, "stop_task", tid)
                } catch (e: Exception) {
                    // melhor esforço
                }
            }
        }
    }

    fun clearHistory() {
        history.value = emptyList()
        store?.saveHistory(emptyList())
    }

    private fun resolveMethod(c: PsConsole): Method = when (c.method) {
        "rpi" -> Method.RPI
        "payload" -> Method.PAYLOAD
        else -> when {
            Ps4Client.isRpiOnline(c.ip, c.port) -> Method.RPI
            Ps4Client.isGoldHenReady(c.ip) -> Method.PAYLOAD
            else -> throw IOException(
                "Nenhum método disponível no PS4. Abra o Remote Package Installer no console ou ative o " +
                    "servidor de payloads do GoldHEN (porta 9090). Use \"Testar conexão\" na aba Console."
            )
        }
    }

    private suspend fun runTransfer(rec: TransferRecord, console: PsConsole, port: Int, item: FileItem, info: PkgInfo?) {
        val id = rec.id
        try {
            val srv = ensureServer(port)
            val localIp = NetUtil.localIpv4()
                ?: throw IOException("Sem rede local. Conecte o celular ao mesmo Wi-Fi do PS4.")
            val tracker = Tracker(item.size)
            trackers[id] = tracker
            srv.register(PkgHttpServer.Entry(id, Uri.parse(item.uri), item.name, item.size))
            serverUrl.value = "http://$localIp:${srv.port}"
            val fileUrl = "http://$localIp:${srv.port}/$id/${Uri.encode(item.name)}"

            patch(id) { it.copy(status = TransferStatus.SENDING, detail = "Detectando o método de instalação do PS4…") }
            val method = resolveMethod(console)

            var tid: Long? = null
            when (method) {
                Method.RPI -> {
                    patch(id) { it.copy(detail = "Enviando comando ao Remote Package Installer…") }
                    val res = Ps4Client.install(console.ip, console.port, fileUrl)
                    if (res.code !in 200..299 || !Ps4Client.isSuccess(res.body)) {
                        var msg = "O PS4 respondeu HTTP ${res.code}: ${res.body.take(160)}"
                        if (res.body.contains("0x80990085")) msg += " (verifique se há espaço livre no PS4)"
                        throw IOException(msg)
                    }
                    tid = Regex("\"task_id\"\\s*:\\s*(\\d+)").find(res.body)?.groupValues?.get(1)?.toLongOrNull()
                    if (tid != null) taskIds[id] = tid
                    val accepted = "PS4 aceitou o pedido" + (if (tid != null) " (tarefa $tid)" else "") +
                        ". Aguardando o PS4 baixar o arquivo…"
                    patch(id) { it.copy(detail = accepted) }
                }

                Method.PAYLOAD -> {
                    patch(id) { it.copy(detail = "Enviando o payload ao GoldHEN do PS4…") }
                    sendViaPayload(id, console, localIp, srv, item, info, fileUrl)
                    patch(id) {
                        it.copy(detail = "Payload entregue. Aguardando o PS4 baixar o arquivo… (erros aparecem como notificação \"DPI: ...\" no PS4)")
                    }
                }
            }

            val waitStart = System.currentTimeMillis()
            var lastTotal = 0L
            var lastTime = waitStart
            var tick = 0
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val total = tracker.total.get()
                val speed = (total - lastTotal) * 1000L / maxOf(1L, now - lastTime)
                lastTotal = total
                lastTime = now
                val covered = tracker.covered()
                tick++
                var detail: String? = null
                if (tid != null && tick % 3 == 0) {
                    try {
                        val r = Ps4Client.taskCommand(console.ip, console.port, "get_task_progress", tid)
                        detail = "PS4: " + r.body.replace("\n", " ").trim().take(140)
                    } catch (e: Exception) {
                        // o app do PS4 pode estar em segundo plano; ignora
                    }
                }
                patch(id) { it.copy(covered = covered, speedBps = speed, detail = detail ?: it.detail) }
                if (covered >= item.size) break
                if (total == 0L && now - waitStart > NO_DOWNLOAD_TIMEOUT_MS) {
                    throw IOException(
                        "O PS4 não baixou nenhum dado em 2 minutos. Veja as notificações do PS4 " +
                            "(\"DPI: ...\" ou erros do instalador) e confira se ele alcança o IP do celular."
                    )
                }
            }
            finish(id, TransferStatus.DONE, "Arquivo enviado por completo ao PS4. A instalação continua no console.")
        } catch (e: CancellationException) {
            finish(
                id, TransferStatus.CANCELLED,
                "Cancelado no app. Se o PS4 já criou o download, cancele-o também nas notificações do PS4."
            )
            throw e
        } catch (e: Exception) {
            finish(id, TransferStatus.FAILED, e.message ?: "Falha desconhecida")
        }
    }

    // ---------- Método payload GoldHEN ----------

    private fun sendViaPayload(
        id: String,
        console: PsConsole,
        localIp: String,
        srv: PkgHttpServer,
        item: FileItem,
        info: PkgInfo?,
        fileUrl: String,
    ) {
        val ctx = appCtx ?: throw IOException("Gerenciador não inicializado")
        if (info == null || !info.recognized) {
            throw IOException("Não consegui ler os metadados deste PKG. O método payload precisa do Content ID e do tipo; tente o método API remota (RPI).")
        }
        val contentId = info.contentId ?: throw IOException("Este PKG não informa o Content ID.")
        val category = info.category
            ?: throw IOException("Este PKG não informa a categoria (CATEGORY) no PARAM.SFO; tente o método API remota (RPI).")

        val digest = PkgParser.readHeaderDigest(ctx, Uri.parse(item.uri))
        val manifest = buildManifest(fileUrl, item.size, digest, item.size > FOUR_GIB)
        srv.registerManifest(id, manifest)
        val manifestUrl = "http://$localIp:${srv.port}/json/$id.json"

        val icon = info.iconPath?.let {
            try {
                File(it).readBytes()
            } catch (e: Exception) {
                null
            }
        }
        val meta = GoldHenPayload.Meta(
            url = manifestUrl,
            name = info.title ?: item.name.removeSuffix(".pkg"),
            contentId = contentId,
            type = "PS4" + category.uppercase(),
            size = item.size,
            icon = icon,
        )

        val session: GoldHenPayload.Session
        synchronized(payloadLock) {
            session = sessions.getOrPut(console.ip) {
                val bytes = ctx.assets.open("dpi_payload.bin").use { it.readBytes() }
                GoldHenPayload.Session(console.ip, localIp, bytes)
            }
            sessionUsers[console.ip] = (sessionUsers[console.ip] ?: 0) + 1
        }
        payloadKey[id] = console.ip
        session.send(meta)
    }

    private fun releasePayload(ip: String) {
        var toFinish: GoldHenPayload.Session? = null
        synchronized(payloadLock) {
            val n = (sessionUsers[ip] ?: 0) - 1
            if (n <= 0) {
                sessionUsers.remove(ip)
                toFinish = sessions.remove(ip)
            } else {
                sessionUsers[ip] = n
            }
        }
        val s = toFinish
        if (s != null) {
            scope.launch {
                try {
                    s.finish()
                } catch (e: Exception) {
                    // melhor esforço
                }
            }
        }
    }

    /** Manifesto JSON no formato que o DirectPackageInstaller entrega ao PS4. */
    private fun buildManifest(fileUrl: String, size: Long, digest: String, split: Boolean): String {
        val pieces = ArrayList<Triple<String, Long, Long>>()
        if (split) {
            var off = 0L
            while (off < size) {
                val len = minOf(FOUR_GIB, size - off)
                pieces.add(Triple("$fileUrl?offset=$off&size=$len", off, len))
                off += len
            }
        } else {
            pieces.add(Triple(fileUrl, 0L, size))
        }
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"originalFileSize\": ").append(size).append(",\n")
        sb.append("  \"packageDigest\": ").append(jsonString(digest)).append(",\n")
        sb.append("  \"numberOfSplitFiles\": ").append(pieces.size).append(",\n")
        sb.append("  \"pieces\": [\n")
        for ((i, p) in pieces.withIndex()) {
            sb.append("    {\n")
            sb.append("      \"url\": ").append(jsonString(p.first)).append(",\n")
            sb.append("      \"fileOffset\": ").append(p.second).append(",\n")
            sb.append("      \"fileSize\": ").append(p.third).append(",\n")
            sb.append("      \"hashValue\": \"0000000000000000000000000000000000000000\"\n")
            sb.append("    }")
            sb.append(if (i < pieces.size - 1) ",\n" else "\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // ---------- Estado ----------

    private fun patch(id: String, f: (TransferRecord) -> TransferRecord) {
        active.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun finish(id: String, status: TransferStatus, detail: String) {
        val cur = active.value.firstOrNull { it.id == id }
        payloadKey.remove(id)?.let { releasePayload(it) }
        if (cur == null) return
        val now = System.currentTimeMillis()
        val total = trackers[id]?.total?.get() ?: 0L
        val duration = maxOf(1L, now - cur.startedAt)
        val done = cur.copy(
            status = status,
            endedAt = now,
            speedBps = 0L,
            avgSpeedBps = total * 1000L / duration,
            detail = detail,
            covered = if (status == TransferStatus.DONE) cur.size else cur.covered,
        )
        active.update { list -> list.filter { it.id != id } }
        history.update { (listOf(done) + it).take(200) }
        store?.saveHistory(history.value)
        jobs.remove(id)
        if (status != TransferStatus.DONE) server?.unregister(id)
        trackers.remove(id)
        taskIds.remove(id)
        consoleOf.remove(id)
        scheduleServerStop()
    }

    @Synchronized
    private fun ensureServer(port: Int): PkgHttpServer {
        stopJob?.cancel()
        server?.let { return it }
        val ctx = appCtx ?: throw IOException("Gerenciador não inicializado")
        val s = PkgHttpServer(ctx.contentResolver) { id, pos, n -> trackers[id]?.add(pos, n.toLong()) }
        s.start(port)
        server = s
        return s
    }

    private fun scheduleServerStop() {
        if (active.value.isNotEmpty()) return
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(60_000)
            if (active.value.isEmpty()) stopServerAndService()
        }
    }

    @Synchronized
    private fun stopServerAndService() {
        server?.stop()
        server = null
        serverUrl.value = null
        val ctx = appCtx ?: return
        ctx.stopService(Intent(ctx, TransferService::class.java))
    }

    private fun ensureService() {
        val ctx = appCtx ?: return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TransferService::class.java))
        } catch (e: Exception) {
            // sem serviço em primeiro plano o envio ainda funciona com o app aberto
        }
    }
}
