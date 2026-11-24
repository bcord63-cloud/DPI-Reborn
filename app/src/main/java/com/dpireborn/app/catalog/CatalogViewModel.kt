package com.dpireborn.app.catalog

import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.UUID

val CatalogFilters = listOf("Todos", "PS4", "Android", "Emuladores", "Homebrew", "Ferramentas", "Streaming", "Outros", "Favoritos")
val CatalogSorts = listOf("recent" to "Mais recentes", "name" to "Nome", "updated" to "Atualizados recentemente")

class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val repo = CatalogRepository(ctx)
    private val store = CatalogStore(ctx)
    private val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager

    val entries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val loading = MutableStateFlow(false)
    val sourceNote = MutableStateFlow("")
    val problem = MutableStateFlow<String?>(null)

    val query = MutableStateFlow("")
    val filter = MutableStateFlow("Todos")
    val sort = MutableStateFlow("recent")
    val selectedId = MutableStateFlow<String?>(null)
    val showDownloads = MutableStateFlow(false)

    val favorites = MutableStateFlow(store.loadFavorites())
    val recents = MutableStateFlow(store.loadRecents())
    val downloads = MutableStateFlow(store.loadDownloads())
    val speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val visible: StateFlow<List<CatalogEntry>> =
        combine(entries, query, filter, sort, favorites) { list, q, f, s, fav ->
            val needle = q.trim().lowercase()
            val filtered = list.filter { e ->
                val a = e.app
                val byFilter = when (f) {
                    "PS4" -> a.platforms.any { it.equals("PS4", true) }
                    "Android" -> a.platforms.any { it.equals("Android", true) }
                    "Emuladores" -> a.category == "Emuladores"
                    "Homebrew" -> a.category == "Homebrew"
                    "Ferramentas" -> a.category == "Ferramentas para PS4" || a.category == "Utilitários"
                    "Streaming" -> a.category == "Streaming"
                    "Outros" -> a.category in listOf("Multimídia", "Navegadores", "Aplicativos diversos")
                    "Favoritos" -> a.id in fav
                    else -> true
                }
                byFilter && (needle.isEmpty() ||
                    a.name.lowercase().contains(needle) ||
                    a.developer.lowercase().contains(needle) ||
                    a.category.lowercase().contains(needle) ||
                    a.description.lowercase().contains(needle) ||
                    a.platforms.any { it.lowercase().contains(needle) })
            }
            when (s) {
                "name" -> filtered.sortedBy { it.app.name.lowercase() }
                "updated" -> filtered.sortedWith(
                    compareByDescending<CatalogEntry> { it.release?.publishedAt ?: 0L }.thenBy { it.app.name.lowercase() }
                )
                else -> filtered.sortedWith(
                    compareByDescending<CatalogEntry> { it.app.added }.thenBy { it.app.name.lowercase() }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        val (apps, origin) = repo.loadApps()
        entries.value = repo.loadEntries(apps)
        sourceNote.value = origin
        // Downloads que estavam em andamento quando o app fechou: volta a acompanhar pelo sistema.
        downloads.value.filter { it.status == DlStatus.RUNNING }.forEach { watch(it.id) }
    }

    private fun say(m: String) {
        messages.tryEmit(m)
    }

    // ---------- Catálogo ----------

    /** Só consulta a rede se a última verificação tem mais de 6 horas (ou nunca houve). */
    fun ensureFresh() {
        val last = entries.value.maxOfOrNull { it.checkedAt } ?: 0L
        if (System.currentTimeMillis() - last > 6 * 3600_000L) refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            try {
                problem.value = repo.fetchRemote()
                val (apps, origin) = repo.loadApps()
                sourceNote.value = origin
                val prev = entries.value.associateBy { it.app.id }
                val sem = Semaphore(4)
                val res = coroutineScope {
                    apps.map { a -> async { sem.withPermit { repo.verify(a, prev[a.id]) } } }.awaitAll()
                }
                entries.value = res
                repo.saveStates(res)
                val bad = res.count { it.status == SourceStatus.UNAVAILABLE }
                if (bad > 0) say("$bad fonte(s) indisponível(is) agora")
            } finally {
                loading.value = false
            }
        }
    }

    fun open(id: String) {
        selectedId.value = id
        val l = (listOf(id) + recents.value.filter { it != id }).take(12)
        recents.value = l
        store.saveRecents(l)
    }

    fun close() {
        selectedId.value = null
    }

    fun toggleFavorite(id: String) {
        val s = if (id in favorites.value) favorites.value - id else favorites.value + id
        favorites.value = s
        store.saveFavorites(s)
    }

    fun clearRecents() {
        recents.value = emptyList()
        store.saveRecents(emptyList())
    }

    // ---------- Downloads (DownloadManager do Android) ----------

    private fun persist() = store.saveDownloads(downloads.value)

    private fun updateRec(id: String, f: (DownloadRecord) -> DownloadRecord) {
        downloads.update { l -> l.map { if (it.id == id) f(it) else it } }
    }

    fun startDownload(e: CatalogEntry) {
        val r = e.release
        val url = r?.downloadUrl
        if (!e.canDownload || r == null || url == null || !UrlGuard.isSafeHttps(url)) {
            say("Este item não tem um arquivo verificado para baixar")
            return
        }
        if (downloads.value.any { it.appId == e.app.id && it.status == DlStatus.RUNNING }) {
            say("Este download já está em andamento")
            return
        }
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (base == null) {
            say("Armazenamento indisponível no momento")
            return
        }
        val fileName = (r.assetName ?: "arquivo").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = File(base, "DPI-Reborn/$fileName")
        try {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(e.app.name)
                .setDescription("DPI Reborn • ${r.version}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "DPI-Reborn/$fileName")
                .addRequestHeader("User-Agent", "DPI-Reborn (Android)")
            val dmId = dm.enqueue(req)
            val rec = DownloadRecord(
                id = UUID.randomUUID().toString(), appId = e.app.id, appName = e.app.name, fileName = fileName,
                url = url, sha256 = r.sha256, dmId = dmId, status = DlStatus.RUNNING, size = r.size, received = 0L,
                startedAt = System.currentTimeMillis(), endedAt = 0L, path = dest.absolutePath,
                detail = "Na fila", hashState = "none",
            )
            downloads.update { listOf(rec) + it }
            persist()
            watch(rec.id)
        } catch (ex: Exception) {
            say("Não foi possível iniciar o download: ${ex.message ?: "erro"}")
        }
    }

    private fun failReason(code: Int): String = when (code) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Sem espaço de armazenamento suficiente"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Armazenamento não encontrado"
        DownloadManager.ERROR_FILE_ERROR -> "Erro ao gravar o arquivo"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "O arquivo já existe"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Falha na transferência dos dados"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Redirecionamentos demais"
        DownloadManager.ERROR_CANNOT_RESUME -> "Não foi possível retomar o download"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Resposta HTTP inesperada do servidor"
        in 400..599 -> "O servidor respondeu HTTP $code"
        else -> "Erro desconhecido ($code)"
    }

    private fun pauseReason(code: Int): String = when (code) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Aguardando conexão"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Aguardando Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Tentando novamente"
        else -> "Pausado"
    }

    private fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val b = ByteArray(65536)
            while (true) {
                val n = s.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun watch(recId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var lastBytes = -1L
            var lastT = 0L
            while (true) {
                val rec = downloads.value.firstOrNull { it.id == recId } ?: return@launch
                if (rec.status != DlStatus.RUNNING) return@launch
                var found = false
                var st = 0
                var got = 0L
                var total = -1L
                var reason = 0
                dm.query(DownloadManager.Query().setFilterById(rec.dmId))?.use { c ->
                    if (c.moveToFirst()) {
                        found = true
                        st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }
                }
                if (!found) {
                    updateRec(recId) { it.copy(status = DlStatus.CANCELLED, endedAt = System.currentTimeMillis(), detail = "Removido do sistema de downloads") }
                    persist()
                    return@launch
                }
                val now = SystemClock.elapsedRealtime()
                if (lastBytes >= 0 && now > lastT) {
                    val inst = ((got - lastBytes) * 1000 / (now - lastT)).coerceAtLeast(0)
                    val prev = speeds.value[recId] ?: inst
                    speeds.update { it + (recId to ((prev + inst) / 2)) }
                }
                lastBytes = got
                lastT = now
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val f = File(rec.path)
                        var hash = "none"
                        var ok = true
                        var detail = "Concluído"
                        if (!f.isFile) {
                            ok = false
                            detail = "Arquivo não encontrado após o download"
                        } else if (rec.sha256 != null) {
                            val actual = try { sha256Of(f) } catch (e: Exception) { "" }
                            if (actual.equals(rec.sha256, ignoreCase = true)) {
                                hash = "ok"
                                detail = "Concluído • SHA-256 conferido"
                            } else {
                                ok = false
                                detail = "O SHA-256 não confere com o publicado: arquivo apagado por segurança"
                                f.delete()
                            }
                        }
                        updateRec(recId) {
                            it.copy(
                                status = if (ok) DlStatus.DONE else DlStatus.FAILED, received = if (f.isFile) f.length() else got,
                                size = if (f.isFile) f.length() else it.size, endedAt = System.currentTimeMillis(),
                                detail = detail, hashState = hash,
                            )
                        }
                        speeds.update { it - recId }
                        persist()
                        say(if (ok) "Download concluído: ${rec.appName}" else detail)
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val msg = failReason(reason)
                        updateRec(recId) { it.copy(status = DlStatus.FAILED, endedAt = System.currentTimeMillis(), detail = msg) }
                        speeds.update { it - recId }
                        persist()
                        say("Falha no download de ${rec.appName}: $msg")
                        return@launch
                    }
                    DownloadManager.STATUS_PAUSED -> updateRec(recId) { it.copy(received = got, size = if (total > 0) total else it.size, detail = pauseReason(reason)) }
                    DownloadManager.STATUS_PENDING -> updateRec(recId) { it.copy(detail = "Na fila") }
                    else -> updateRec(recId) { it.copy(received = got, size = if (total > 0) total else it.size, detail = "Baixando") }
                }
                delay(500)
            }
        }
    }

    fun cancelDownload(recId: String) {
        val rec = downloads.value.firstOrNull { it.id == recId } ?: return
        try {
            dm.remove(rec.dmId)
        } catch (e: Exception) {
            // já removido
        }
        File(rec.path).takeIf { it.isFile }?.delete()
        updateRec(recId) { it.copy(status = DlStatus.CANCELLED, endedAt = System.currentTimeMillis(), detail = "Cancelado por você") }
        speeds.update { it - recId }
        persist()
    }

    /** Tira do histórico. Com [deleteFile], apaga também o arquivo baixado. */
    fun removeRecord(recId: String, deleteFile: Boolean) {
        val rec = downloads.value.firstOrNull { it.id == recId } ?: return
        if (rec.status == DlStatus.RUNNING) cancelDownload(recId)
        if (deleteFile) File(rec.path).takeIf { it.isFile }?.delete()
        downloads.update { l -> l.filter { it.id != recId } }
        persist()
    }

    fun clearFinished() {
        downloads.update { l -> l.filter { it.status == DlStatus.RUNNING } }
        persist()
    }
}
