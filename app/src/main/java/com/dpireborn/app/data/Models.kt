package com.dpireborn.app.data

data class LibraryFolder(val uri: String, val name: String)

data class FileItem(
    val uri: String,
    val parentUri: String,
    val rootUri: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val mime: String,
) {
    val isPkg: Boolean get() = name.endsWith(".pkg", ignoreCase = true)
}

data class PkgInfo(
    val recognized: Boolean,
    val title: String? = null,
    val titleId: String? = null,
    val contentId: String? = null,
    val appVersion: String? = null,
    val category: String? = null,
    val iconPath: String? = null,
)

/** method: "auto", "rpi" (API do Remote Package Installer, porta 12800) ou "payload" (servidor de payloads do GoldHEN). */
data class PsConsole(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int,
    val method: String = "auto",
)

fun methodLabel(method: String): String = when (method) {
    "rpi" -> "API remota (RPI)"
    "payload" -> "Payload GoldHEN"
    else -> "Automático"
}

enum class TransferStatus { QUEUED, SENDING, DONE, FAILED, CANCELLED }

data class TransferRecord(
    val id: String,
    val fileName: String,
    val fileUri: String,
    val size: Long,
    val consoleName: String,
    val consoleIp: String,
    val status: TransferStatus,
    val covered: Long,
    val speedBps: Long,
    val startedAt: Long,
    val endedAt: Long,
    val avgSpeedBps: Long,
    val detail: String,
) {
    val progress: Float
        get() = if (size <= 0L) 0f else (covered.toDouble() / size.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val isActive: Boolean
        get() = status == TransferStatus.QUEUED || status == TransferStatus.SENDING
}

data class TestResult(val ok: Boolean, val lines: List<String>, val timestamp: Long)
