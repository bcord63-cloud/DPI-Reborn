package com.dpireborn.app.catalog

/** Entrada do catálogo (vem do catalog.json: remoto, em cache ou embutido). Nada aqui é fixo no código. */
data class CatalogApp(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val platforms: List<String>,
    val developer: String,
    val sourceUrl: String,
    val github: String?,
    val assetRegex: String?,
    val license: String?,
    val added: Long,
    val removed: Boolean,
)

/** Dados lidos ao vivo da fonte (release do GitHub). */
data class ResolvedRelease(
    val version: String,
    val publishedAt: Long,
    val publisher: String?,
    val assetName: String?,
    val size: Long,
    val downloadUrl: String?,
    val sha256: String?,
    val prerelease: Boolean,
    val releaseUrl: String?,
)

enum class SourceStatus { DOWNLOAD, LINK, UNCHECKED, UNAVAILABLE }

data class CatalogEntry(
    val app: CatalogApp,
    val release: ResolvedRelease?,
    val status: SourceStatus,
    val note: String?,
    val checkedAt: Long,
) {
    val canDownload: Boolean get() = status == SourceStatus.DOWNLOAD && release?.downloadUrl != null
}

enum class DlStatus { RUNNING, DONE, FAILED, CANCELLED }

data class DownloadRecord(
    val id: String,
    val appId: String,
    val appName: String,
    val fileName: String,
    val url: String,
    val sha256: String?,
    val dmId: Long,
    val status: DlStatus,
    val size: Long,
    val received: Long,
    val startedAt: Long,
    val endedAt: Long,
    val path: String,
    val detail: String,
    val hashState: String, // "ok", "none"
) {
    val progress: Float
        get() = if (size <= 0L) 0f else (received.toDouble() / size.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val isPkg: Boolean get() = fileName.endsWith(".pkg", ignoreCase = true)
}

object UrlGuard {
    private val ipLike = Regex("^[0-9.]+$")

    /** Aceita só HTTPS com nome de domínio público (sem IP, localhost ou credenciais na URL). */
    fun isSafeHttps(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val u = try {
            java.net.URI(url)
        } catch (e: Exception) {
            return false
        }
        if (!"https".equals(u.scheme, ignoreCase = true)) return false
        val h = u.host ?: return false
        if (u.userInfo != null) return false
        if (h == "localhost" || h.endsWith(".local") || h.contains(":") || ipLike.matches(h)) return false
        return h.contains('.')
    }

    fun host(url: String): String = try {
        java.net.URI(url).host ?: url
    } catch (e: Exception) {
        url
    }
}
