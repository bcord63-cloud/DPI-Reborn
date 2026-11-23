package com.dpireborn.app.net

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Cliente da API do "Remote Package Installer" (RPI) / DPI v2, que escuta em HTTP na porta 12800
 * do PS4 (documentado em github.com/flatz/ps4_remote_pkg_installer).
 *
 * Endpoints usados (todos POST com corpo JSON):
 *  - /api/install        {"type":"direct","packages":["http://..."]}
 *  - /api/get_task_progress / stop_task   {"task_id":N}
 *  - /api/is_exists      {"title_id":"CUSA..."}   (usado só para testar se a API responde)
 *
 * O formato das respostas não é documentado em detalhe; por isso o app mostra o texto bruto
 * e só extrai "task_id" quando ele existe.
 */
object Ps4Client {
    data class PortCheck(val port: Int, val open: Boolean, val ms: Long)
    data class HttpResult(val code: Int, val body: String)

    fun checkPort(ip: String, port: Int, timeoutMs: Int = 2000): PortCheck {
        val t0 = System.currentTimeMillis()
        return try {
            Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
            PortCheck(port, true, System.currentTimeMillis() - t0)
        } catch (e: Exception) {
            PortCheck(port, false, System.currentTimeMillis() - t0)
        }
    }

    fun postJson(ip: String, port: Int, path: String, json: String, timeoutMs: Int = 8000): HttpResult {
        val conn = URL("http://$ip:$port$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return HttpResult(code, body)
        } finally {
            conn.disconnect()
        }
    }

    fun install(ip: String, port: Int, packageUrl: String): HttpResult {
        val json = JSONObject()
            .put("type", "direct")
            .put("packages", JSONArray().put(packageUrl))
            .toString()
        return postJson(ip, port, "/api/install", json)
    }

    fun taskCommand(ip: String, port: Int, command: String, taskId: Long): HttpResult =
        postJson(ip, port, "/api/$command", "{\"task_id\":$taskId}", 5000)

    fun probeApi(ip: String, port: Int): HttpResult =
        postJson(ip, port, "/api/is_exists", "{\"title_id\":\"CUSA00000\"}", 5000)

    private fun httpGet(url: String, timeoutMs: Int): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                stream?.bufferedReader()?.use { it.readText() } ?: ""
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mesma checagem do DirectPackageInstaller: GET http://IP:9090/status devolve
     * "status":"ready" quando o servidor de payloads do GoldHEN está ativo.
     */
    fun isGoldHenReady(ip: String): Boolean =
        httpGet("http://$ip:9090/status", 1500)?.replace(" ", "")?.contains("\"status\":\"ready\"") == true

    /** Mesma checagem do DPI: GET /api no Remote Package Installer responde "Unsupported method ... fail". */
    fun isRpiOnline(ip: String, port: Int): Boolean {
        val body = httpGet("http://$ip:$port/api", 1500) ?: return false
        return body.contains("Unsupported method") && body.contains("fail")
    }

    /** O RPI considera sucesso quando a resposta contém "success" entre aspas (como o DPI verifica). */
    fun isSuccess(body: String): Boolean = body.contains("\"success\"")
}
