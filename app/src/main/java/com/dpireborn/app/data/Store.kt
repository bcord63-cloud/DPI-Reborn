package com.dpireborn.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

/** Persistência simples em JSON + SharedPreferences (sem dependências extras). */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("dpi_reborn", Context.MODE_PRIVATE)
    private val foldersFile = File(ctx.filesDir, "folders.json")
    private val consolesFile = File(ctx.filesDir, "consoles.json")
    private val historyFile = File(ctx.filesDir, "history.json")
    private val importedFile = File(ctx.filesDir, "imported.json")
    private val pkgCacheFile = File(ctx.cacheDir, "pkg_cache.json")

    private fun readArray(f: File): JSONArray = try {
        if (f.exists()) JSONArray(f.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }

    private fun writeText(f: File, text: String) {
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) f.writeText(text)
        } catch (e: Exception) {
            // ignora falhas de escrita; os dados serão regravados na próxima alteração
        }
    }

    private fun <T> JSONArray.objs(block: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>()
        for (i in 0 until length()) {
            try {
                out.add(block(getJSONObject(i)))
            } catch (e: Exception) {
                // item corrompido: ignora
            }
        }
        return out
    }

    // ---------- Pastas ----------
    fun loadFolders(): List<LibraryFolder> =
        readArray(foldersFile).objs { LibraryFolder(it.getString("uri"), it.optString("name", "Pasta")) }

    fun saveFolders(list: List<LibraryFolder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        writeText(foldersFile, arr.toString())
    }

    // ---------- PKG importados individualmente ----------
    fun loadImported(): List<String> {
        val a = readArray(importedFile)
        val out = ArrayList<String>()
        for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    fun saveImported(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        writeText(importedFile, arr.toString())
    }

    // ---------- Consoles ----------
    fun loadConsoles(): List<PsConsole> =
        readArray(consolesFile).objs {
            PsConsole(
                it.getString("id"),
                it.optString("name", "PS4"),
                it.getString("ip"),
                it.optInt("port", 12800),
                it.optString("method", "auto"),
            )
        }

    fun saveConsoles(list: List<PsConsole>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("ip", it.ip)
                    .put("port", it.port).put("method", it.method)
            )
        }
        writeText(consolesFile, arr.toString())
    }

    // ---------- Histórico ----------
    fun loadHistory(): List<TransferRecord> =
        readArray(historyFile).objs {
            TransferRecord(
                id = it.getString("id"),
                fileName = it.optString("fileName"),
                fileUri = it.optString("fileUri"),
                size = it.optLong("size"),
                consoleName = it.optString("consoleName"),
                consoleIp = it.optString("consoleIp"),
                status = try {
                    TransferStatus.valueOf(it.optString("status", "FAILED"))
                } catch (e: Exception) {
                    TransferStatus.FAILED
                },
                covered = it.optLong("covered"),
                speedBps = 0L,
                startedAt = it.optLong("startedAt"),
                endedAt = it.optLong("endedAt"),
                avgSpeedBps = it.optLong("avgSpeedBps"),
                detail = it.optString("detail"),
            )
        }

    fun saveHistory(list: List<TransferRecord>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("fileName", it.fileName)
                    .put("fileUri", it.fileUri)
                    .put("size", it.size)
                    .put("consoleName", it.consoleName)
                    .put("consoleIp", it.consoleIp)
                    .put("status", it.status.name)
                    .put("covered", it.covered)
                    .put("startedAt", it.startedAt)
                    .put("endedAt", it.endedAt)
                    .put("avgSpeedBps", it.avgSpeedBps)
                    .put("detail", it.detail)
            )
        }
        writeText(historyFile, arr.toString())
    }

    // ---------- Cache de metadados dos PKG ----------
    fun loadPkgCache(): Map<String, PkgInfo> {
        val out = HashMap<String, PkgInfo>()
        try {
            if (!pkgCacheFile.exists()) return out
            val root = JSONObject(pkgCacheFile.readText())
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                try {
                    val o = root.getJSONObject(k)
                    out[k] = PkgInfo(
                        recognized = o.optBoolean("recognized", false),
                        title = o.str("title"),
                        titleId = o.str("titleId"),
                        contentId = o.str("contentId"),
                        appVersion = o.str("appVersion"),
                        category = o.str("category"),
                        iconPath = o.str("iconPath"),
                    )
                } catch (e: Exception) {
                    // entrada inválida
                }
            }
        } catch (e: Exception) {
            // cache corrompido: recomeça vazio
        }
        return out
    }

    fun savePkgCache(map: Map<String, PkgInfo>) {
        val root = JSONObject()
        for ((k, v) in map) {
            val o = JSONObject().put("recognized", v.recognized)
            v.title?.let { o.put("title", it) }
            v.titleId?.let { o.put("titleId", it) }
            v.contentId?.let { o.put("contentId", it) }
            v.appVersion?.let { o.put("appVersion", it) }
            v.category?.let { o.put("category", it) }
            v.iconPath?.let { o.put("iconPath", it) }
            root.put(k, o)
        }
        writeText(pkgCacheFile, root.toString())
    }

    fun deletePkgCache() {
        try {
            pkgCacheFile.delete()
        } catch (e: Exception) {
        }
    }

    // ---------- Preferências ----------
    fun accent(): String = prefs.getString("accent", "violet") ?: "violet"
    fun setAccent(v: String) = prefs.edit().putString("accent", v).apply()

    fun gridView(): Boolean = prefs.getBoolean("grid", true)
    fun setGridView(v: Boolean) = prefs.edit().putBoolean("grid", v).apply()

    fun serverPort(): Int = prefs.getInt("server_port", 8787)
    fun setServerPort(v: Int) = prefs.edit().putInt("server_port", v).apply()

    fun selectedConsole(): String? = prefs.getString("console", null)
    fun setSelectedConsole(v: String?) = prefs.edit().putString("console", v).apply()
}
