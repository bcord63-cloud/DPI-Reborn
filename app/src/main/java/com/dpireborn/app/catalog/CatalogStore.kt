package com.dpireborn.app.catalog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Favoritos, apps vistos recentemente e histórico de downloads, guardados em JSON no armazenamento privado do app. */
class CatalogStore(ctx: Context) {
    private val favFile = File(ctx.filesDir, "fav_apps.json")
    private val recentFile = File(ctx.filesDir, "recent_apps.json")
    private val dlFile = File(ctx.filesDir, "downloads.json")

    private fun read(f: File): JSONArray = try {
        if (f.exists()) JSONArray(f.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }

    private fun write(f: File, text: String) {
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) f.writeText(text)
        } catch (e: Exception) {
            // regrava na próxima alteração
        }
    }

    private fun strings(f: File): List<String> {
        val a = read(f)
        val out = ArrayList<String>()
        for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    private fun saveStrings(f: File, l: Collection<String>) {
        val a = JSONArray()
        l.forEach { a.put(it) }
        write(f, a.toString())
    }

    fun loadFavorites(): Set<String> = strings(favFile).toSet()
    fun saveFavorites(s: Set<String>) = saveStrings(favFile, s)
    fun loadRecents(): List<String> = strings(recentFile)
    fun saveRecents(l: List<String>) = saveStrings(recentFile, l)

    fun loadDownloads(): List<DownloadRecord> {
        val a = read(dlFile)
        val out = ArrayList<DownloadRecord>()
        for (i in 0 until a.length()) {
            try {
                val o = a.getJSONObject(i)
                out.add(
                    DownloadRecord(
                        id = o.getString("id"), appId = o.getString("appId"), appName = o.optString("appName"),
                        fileName = o.getString("fileName"), url = o.getString("url"),
                        sha256 = if (o.isNull("sha256")) null else o.optString("sha256").ifBlank { null },
                        dmId = o.optLong("dmId"), status = DlStatus.valueOf(o.getString("status")),
                        size = o.optLong("size"), received = o.optLong("received"),
                        startedAt = o.optLong("startedAt"), endedAt = o.optLong("endedAt"),
                        path = o.optString("path"), detail = o.optString("detail"),
                        hashState = o.optString("hashState", "none"),
                    )
                )
            } catch (e: Exception) {
                // registro corrompido
            }
        }
        return out
    }

    fun saveDownloads(list: List<DownloadRecord>) {
        val a = JSONArray()
        for (r in list) {
            a.put(
                JSONObject().put("id", r.id).put("appId", r.appId).put("appName", r.appName)
                    .put("fileName", r.fileName).put("url", r.url).put("sha256", r.sha256 ?: JSONObject.NULL)
                    .put("dmId", r.dmId).put("status", r.status.name).put("size", r.size)
                    .put("received", r.received).put("startedAt", r.startedAt).put("endedAt", r.endedAt)
                    .put("path", r.path).put("detail", r.detail).put("hashState", r.hashState)
            )
        }
        write(dlFile, a.toString())
    }
}
