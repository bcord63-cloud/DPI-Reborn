package com.dpireborn.app.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private class GhHttpException(val code: Int) : Exception("HTTP $code")

/**
 * Catálogo atualizável: lê o catalog.json (online > cache > embutido) e confere cada fonte ao vivo.
 * Versão, tamanho, data, link e hash dos arquivos vêm da própria fonte (API de releases do GitHub);
 * o catálogo só diz QUAL projeto/página existe e em que categoria ele fica.
 */
class CatalogRepository(private val ctx: Context) {
    companion object {
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/bcord63-cloud/DPI-Reborn/main/catalog/catalog.json"
        private const val UA = "DPI-Reborn (Android)"
        private const val GH_ACCEPT = "application/vnd.github+json"
        private const val MAX_BYTES = 1_000_000
        private val repoRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    }

    private val remoteFile = File(ctx.filesDir, "catalog_remote.json")
    private val stateFile = File(ctx.filesDir, "catalog_state.json")

    private class Http(val code: Int, val body: String)

    private fun dateOf(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd")
        for (p in patterns) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                return f.parse(s)?.time ?: continue
            } catch (e: Exception) {
                // tenta o próximo formato
            }
        }
        return 0L
    }

    private fun JSONArray?.strings(): List<String> {
        val out = ArrayList<String>()
        if (this == null) return out
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    fun parseCatalog(text: String): List<CatalogApp> {
        val arr = JSONObject(text).getJSONArray("apps")
        val out = ArrayList<CatalogApp>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val src = o.getString("sourceUrl")
                if (!UrlGuard.isSafeHttps(src)) continue // fonte sem HTTPS válido não entra
                val gh = o.optString("github", "").takeIf { repoRegex.matches(it) }
                out.add(
                    CatalogApp(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        description = o.optString("description", ""),
                        category = o.optString("category", "Aplicativos diversos"),
                        platforms = o.optJSONArray("platforms").strings(),
                        developer = o.optString("developer", "Não informado"),
                        sourceUrl = src,
                        github = gh,
                        assetRegex = o.optString("assetRegex", "").ifBlank { null },
                        license = o.optString("license", "").ifBlank { null },
                        added = dateOf(o.optString("added", "")),
                        removed = o.optBoolean("removed", false),
                    )
                )
            } catch (e: Exception) {
                // entrada malformada: ignora só ela
            }
        }
        return out.filter { !it.removed }.distinctBy { it.id }
    }

    /** Catálogo em uso: cópia baixada da internet, se existir; senão o embutido no app. */
    fun loadApps(): Pair<List<CatalogApp>, String> {
        try {
            if (remoteFile.exists()) {
                val l = parseCatalog(remoteFile.readText())
                if (l.isNotEmpty()) return l to "Catálogo atualizado pela internet (cópia salva)"
            }
        } catch (e: Exception) {
            // cópia corrompida: cai para o embutido
        }
        val text = ctx.assets.open("catalog.json").bufferedReader().use { it.readText() }
        return parseCatalog(text) to "Catálogo embutido no app"
    }

    private fun request(url: String, method: String = "GET", accept: String? = null, readBody: Boolean = true): Http {
        require(UrlGuard.isSafeHttps(url)) { "URL não permitida" }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            if (accept != null) c.setRequestProperty("Accept", accept)
            val code = c.responseCode
            var body = ""
            if (readBody && code in 200..299) {
                c.inputStream.use { s ->
                    val buf = ByteArrayOutputStream()
                    val b = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = s.read(b)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Resposta grande demais")
                        buf.write(b, 0, n)
                    }
                    body = buf.toString("UTF-8")
                }
            }
            return Http(code, body)
        } finally {
            c.disconnect()
        }
    }

    /** Baixa o catalog.json do repositório. Devolve uma mensagem de problema, ou null se atualizou. */
    suspend fun fetchRemote(): String? = withContext(Dispatchers.IO) {
        try {
            val r = request(CATALOG_URL, accept = "application/json")
            if (r.code != 200) {
                return@withContext "Catálogo online indisponível (HTTP ${r.code}); usando o último catálogo salvo"
            }
            if (parseCatalog(r.body).isEmpty()) {
                return@withContext "Catálogo online inválido ou vazio; usando o último catálogo salvo"
            }
            val tmp = File(remoteFile.parentFile, remoteFile.name + ".tmp")
            tmp.writeText(r.body)
            if (!tmp.renameTo(remoteFile)) remoteFile.writeText(r.body)
            null
        } catch (e: Exception) {
            "Sem conexão com o catálogo online; usando o último catálogo salvo"
        }
    }

    suspend fun verify(app: CatalogApp, previous: CatalogEntry?): CatalogEntry = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            if (app.github != null) verifyGithub(app, now) else verifyPage(app, now)
        } catch (e: GhHttpException) {
            if (e.code == 403 || e.code == 429) {
                CatalogEntry(
                    app, previous?.release, previous?.status ?: SourceStatus.UNCHECKED,
                    "Limite de consultas do GitHub atingido; mostrando a última verificação", previous?.checkedAt ?: 0L,
                )
            } else {
                CatalogEntry(app, null, SourceStatus.UNAVAILABLE, "O GitHub respondeu HTTP ${e.code}", now)
            }
        } catch (e: Exception) {
            CatalogEntry(
                app, previous?.release, SourceStatus.UNAVAILABLE,
                "Não foi possível consultar a fonte agora (sem internet ou site fora do ar)", now,
            )
        }
    }

    private fun latestRelease(base: String): JSONObject? {
        val r = request("$base/releases/latest", accept = GH_ACCEPT)
        if (r.code == 200) return JSONObject(r.body)
        if (r.code != 404) throw GhHttpException(r.code)
        // Sem "latest": o projeto pode ter só pré-lançamentos.
        val l = request("$base/releases?per_page=5", accept = GH_ACCEPT)
        if (l.code == 404) return null
        if (l.code != 200) throw GhHttpException(l.code)
        val a = JSONArray(l.body)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (!o.optBoolean("draft")) return o
        }
        return null
    }

    private fun verifyGithub(app: CatalogApp, now: Long): CatalogEntry {
        val rel = latestRelease("https://api.github.com/repos/${app.github}")
            ?: return CatalogEntry(app, null, SourceStatus.UNAVAILABLE, "Nenhuma release publicada neste repositório", now)
        val tag = rel.optString("tag_name", "").ifBlank { rel.optString("name", "?") }
        val pre = rel.optBoolean("prerelease")
        val published = dateOf(rel.optString("published_at", ""))
        val publisher = rel.optJSONObject("author")?.optString("login", "")?.ifBlank { null }
        val htmlUrl = rel.optString("html_url", "").takeIf { UrlGuard.isSafeHttps(it) }
        val re = app.assetRegex?.let { try { Regex(it) } catch (e: Exception) { null } }
        val assets = rel.optJSONArray("assets") ?: JSONArray()
        var chosen: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (re == null || re.containsMatchIn(a.optString("name", ""))) {
                chosen = a
                break
            }
        }
        val dl = chosen?.optString("browser_download_url", "")
        val host = if (dl != null) UrlGuard.host(dl) else ""
        val okUrl = dl != null && UrlGuard.isSafeHttps(dl) && (host == "github.com" || host.endsWith(".github.com") || host.endsWith("githubusercontent.com"))
        if (chosen == null || !okUrl) {
            val r = ResolvedRelease(tag, published, publisher, null, 0L, null, null, pre, htmlUrl)
            return CatalogEntry(
                app, r, SourceStatus.LINK,
                "Release $tag encontrada, mas sem arquivo compatível para baixar aqui. Use a página oficial.", now,
            )
        }
        val digest = chosen.optString("digest", "").removePrefix("sha256:").takeIf { it.length == 64 }
        val r = ResolvedRelease(
            version = tag, publishedAt = published, publisher = publisher,
            assetName = chosen.optString("name", "arquivo"), size = chosen.optLong("size", 0L),
            downloadUrl = dl, sha256 = digest, prerelease = pre, releaseUrl = htmlUrl,
        )
        return CatalogEntry(app, r, SourceStatus.DOWNLOAD, null, now)
    }

    private fun verifyPage(app: CatalogApp, now: Long): CatalogEntry {
        var r = request(app.sourceUrl, "HEAD", readBody = false)
        if (r.code == 403 || r.code == 405 || r.code == 501) r = request(app.sourceUrl, "GET", readBody = false)
        return when {
            r.code in 200..399 -> CatalogEntry(app, null, SourceStatus.LINK, "Página oficial respondeu normalmente", now)
            r.code == 401 || r.code == 403 || r.code == 429 || r.code == 999 -> CatalogEntry(
                app, null, SourceStatus.LINK,
                "O site não permite verificação automática; abra a página oficial para confirmar", now,
            )
            else -> CatalogEntry(app, null, SourceStatus.UNAVAILABLE, "A página oficial respondeu HTTP ${r.code}", now)
        }
    }

    // ---------- Última verificação salva (para mostrar offline) ----------

    fun saveStates(list: List<CatalogEntry>) {
        try {
            val arr = JSONArray()
            for (e in list) {
                val o = JSONObject().put("id", e.app.id).put("status", e.status.name)
                    .put("note", e.note ?: JSONObject.NULL).put("checkedAt", e.checkedAt)
                e.release?.let { r ->
                    o.put(
                        "release",
                        JSONObject().put("version", r.version).put("publishedAt", r.publishedAt)
                            .put("publisher", r.publisher ?: JSONObject.NULL)
                            .put("assetName", r.assetName ?: JSONObject.NULL).put("size", r.size)
                            .put("url", r.downloadUrl ?: JSONObject.NULL).put("sha256", r.sha256 ?: JSONObject.NULL)
                            .put("pre", r.prerelease).put("page", r.releaseUrl ?: JSONObject.NULL)
                    )
                }
                arr.put(o)
            }
            val tmp = File(stateFile.parentFile, stateFile.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(stateFile)) stateFile.writeText(arr.toString())
        } catch (e: Exception) {
            // cache é só conveniência
        }
    }

    fun loadEntries(apps: List<CatalogApp>): List<CatalogEntry> {
        val saved = HashMap<String, JSONObject>()
        try {
            if (stateFile.exists()) {
                val arr = JSONArray(stateFile.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    saved[o.getString("id")] = o
                }
            }
        } catch (e: Exception) {
            saved.clear()
        }
        fun s(o: JSONObject, k: String): String? = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
        return apps.map { a ->
            val o = saved[a.id] ?: return@map CatalogEntry(a, null, SourceStatus.UNCHECKED, null, 0L)
            try {
                val r = o.optJSONObject("release")?.let {
                    ResolvedRelease(
                        it.getString("version"), it.optLong("publishedAt"), s(it, "publisher"), s(it, "assetName"),
                        it.optLong("size"), s(it, "url")?.takeIf { u -> UrlGuard.isSafeHttps(u) }, s(it, "sha256"),
                        it.optBoolean("pre"), s(it, "page"),
                    )
                }
                CatalogEntry(a, r, SourceStatus.valueOf(o.getString("status")), s(o, "note"), o.optLong("checkedAt"))
            } catch (e: Exception) {
                CatalogEntry(a, null, SourceStatus.UNCHECKED, null, 0L)
            }
        }
    }
}
