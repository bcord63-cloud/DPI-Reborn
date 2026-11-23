package com.dpireborn.app.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.dpireborn.app.data.FileItem
import com.dpireborn.app.data.PsConsole
import com.dpireborn.app.data.Store
import com.dpireborn.app.data.TransferRecord
import com.dpireborn.app.data.TransferStatus
import com.dpireborn.app.net.NetUtil
import com.dpireborn.app.net.Ps4Client
import com.dpireborn.app.net.PkgHttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Orquestra os envios: sobe o servidor HTTP local, pede ao PS4 (via API do instalador remoto)
 * para baixar o PKG desse servidor e acompanha quantos bytes distintos o PS4 já leu.
 *
 * O progresso mostrado é o do arquivo servido ao PS4 (dados reais), não uma estimativa
 * da instalação no console.
 */
object TransferManager {
    private var appCtx: Context? = null
    private var store: Store? = null
    private var server: PkgHttpServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val trackers = ConcurrentHashMap<String, Tracker>()
    private val taskIds = ConcurrentHashMap<String, Long>()
    private val consoleOf = ConcurrentHashMap<String, PsConsole>()
    private var stopJob: Job? = null

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

    fun enqueue(ctx: Context, items: List<FileItem>, console: PsConsole, preferredPort: Int) {
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
            jobs[id] = scope.launch { runTransfer(rec, console, preferredPort, item) }
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

    private suspend fun runTransfer(rec: TransferRecord, console: PsConsole, port: Int, item: FileItem) {
        val id = rec.id
        try {
            val srv = ensureServer(port)
            val localIp = NetUtil.localIpv4()
                ?: throw IOException("Sem rede local. Conecte o celular ao mesmo Wi-Fi do PS4.")
            val tracker = Tracker(item.size)
            trackers[id] = tracker
            srv.register(PkgHttpServer.Entry(id, Uri.parse(item.uri), item.name, item.size))
            serverUrl.value = "http://$localIp:${srv.port}"
            val url = "http://$localIp:${srv.port}/$id/${Uri.encode(item.name)}"

            patch(id) { it.copy(status = TransferStatus.SENDING, detail = "Enviando comando de instalação ao PS4…") }
            val res = Ps4Client.install(console.ip, console.port, url)
            if (res.code !in 200..299) {
                throw IOException("O PS4 respondeu HTTP ${res.code}: ${res.body.take(120)}")
            }
            val tid = Regex("\"task_id\"\\s*:\\s*(\\d+)").find(res.body)?.groupValues?.get(1)?.toLongOrNull()
            if (tid != null) taskIds[id] = tid
            val accepted = "PS4 aceitou o pedido" + (if (tid != null) " (tarefa $tid)" else "") +
                ". Aguardando o PS4 baixar o arquivo…"
            patch(id) { it.copy(detail = accepted) }

            var lastTotal = 0L
            var lastTime = System.currentTimeMillis()
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
            }
            finish(id, TransferStatus.DONE, "Arquivo enviado por completo ao PS4. A instalação continua no console.")
        } catch (e: CancellationException) {
            finish(id, TransferStatus.CANCELLED, "Cancelado pelo usuário")
            throw e
        } catch (e: Exception) {
            finish(id, TransferStatus.FAILED, e.message ?: "Falha desconhecida")
        }
    }

    private fun patch(id: String, f: (TransferRecord) -> TransferRecord) {
        active.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun finish(id: String, status: TransferStatus, detail: String) {
        val cur = active.value.firstOrNull { it.id == id } ?: return
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
