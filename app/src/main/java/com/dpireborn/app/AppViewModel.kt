package com.dpireborn.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dpireborn.app.data.FileItem
import com.dpireborn.app.data.FileScanner
import com.dpireborn.app.data.LibraryFolder
import com.dpireborn.app.data.PkgInfo
import com.dpireborn.app.data.PkgParser
import com.dpireborn.app.data.PsConsole
import com.dpireborn.app.data.Store
import com.dpireborn.app.data.StoragePermission
import com.dpireborn.app.data.TestResult
import com.dpireborn.app.net.Ps4Client
import com.dpireborn.app.transfer.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val store = Store(ctx)
    private val pkgCache = HashMap<String, PkgInfo>()
    private var rescanAgain = false

    // Biblioteca / arquivos
    val folders = MutableStateFlow(store.loadFolders())
    val files = MutableStateFlow<List<FileItem>>(emptyList())
    val pkgInfo = MutableStateFlow<Map<String, PkgInfo>>(emptyMap())
    val scanning = MutableStateFlow(false)
    val selection = MutableStateFlow<Set<String>>(emptySet())
    val imported = MutableStateFlow(store.loadImported())
    /** Problemas por pasta (chave = uri da pasta): permissão revogada, pasta indisponível. */
    val folderProblems = MutableStateFlow<Map<String, String>>(emptyMap())

    // Consoles
    val consoles = MutableStateFlow(store.loadConsoles())
    val selectedConsoleId = MutableStateFlow(store.selectedConsole())
    val testResults = MutableStateFlow<Map<String, TestResult>>(emptyMap())
    val testing = MutableStateFlow<Set<String>>(emptySet())

    // Configurações
    val accent = MutableStateFlow(store.accent())
    val gridView = MutableStateFlow(store.gridView())
    val serverPort = MutableStateFlow(store.serverPort())

    // Transferências (estado vivo vem do TransferManager)
    val active = TransferManager.active
    val history = TransferManager.history
    val serverUrl = TransferManager.serverUrl

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    init {
        TransferManager.init(ctx)
        pkgCache.putAll(store.loadPkgCache())
        rescan()
    }

    private fun say(msg: String) {
        messages.tryEmit(msg)
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)

    // ---------------- Pastas e varredura ----------------

    fun addFolder(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            say("Não foi possível manter a permissão desta pasta")
        }
        if (folders.value.any { it.uri == uri.toString() }) {
            say("Esta pasta já foi adicionada")
            return
        }
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            ""
        }
        val name = docId.substringAfterLast('/').substringAfter(':').ifBlank { "Armazenamento" }
        val list = folders.value + LibraryFolder(uri.toString(), name)
        folders.value = list
        store.saveFolders(list)
        rescan()
    }

    /** Procurar PKG em todo o celular (exige "Acesso a todos os arquivos"). */
    fun addAllStorage() {
        if (!StoragePermission.hasAllFiles(ctx)) {
            say("Permissão \"Acesso a todos os arquivos\" não concedida")
            return
        }
        if (folders.value.any { it.uri == StoragePermission.ALL_STORAGE_URI }) {
            rescan()
            return
        }
        val list = folders.value + LibraryFolder(StoragePermission.ALL_STORAGE_URI, "Todo o celular")
        folders.value = list
        store.saveFolders(list)
        rescan()
    }

    /** Importa um único PKG (OpenDocument): confere extensão e assinatura do cabeçalho antes de aceitar. */
    fun importPkg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // alguns provedores não permitem permissão persistente; vale só até reiniciar
                }
                val item = FileScanner.statSingle(ctx, uri)
                if (item == null) {
                    say("Falha ao importar: não foi possível acessar o arquivo")
                    return@launch
                }
                if (!item.isPkg) {
                    say("Arquivo inválido: escolha um arquivo .pkg")
                    return@launch
                }
                val magic = ByteArray(4)
                val n = ctx.contentResolver.openInputStream(uri)?.use { it.read(magic) } ?: -1
                if (n < 4 || magic[0] != 0x7F.toByte() || magic[1] != 0x43.toByte() ||
                    magic[2] != 0x4E.toByte() || magic[3] != 0x54.toByte()
                ) {
                    say("Arquivo inválido: o cabeçalho não é de um PKG de PS4")
                    return@launch
                }
                if (imported.value.contains(uri.toString())) {
                    say("Este PKG já foi importado")
                    return@launch
                }
                val list = imported.value + uri.toString()
                imported.value = list
                store.saveImported(list)
                say("PKG importado: ${item.name}")
                rescan()
            } catch (e: Exception) {
                say("Falha ao importar: ${e.message ?: "erro de leitura"}")
            }
        }
    }

    /** Envia ao PS4 um PKG baixado pelo catálogo, reaproveitando o mesmo mecanismo de envio. */
    fun sendDownloaded(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(path)
            if (!f.isFile) {
                say("Arquivo não encontrado: ele foi movido ou apagado")
                return@launch
            }
            val uri = Uri.fromFile(f)
            val item = FileScanner.statSingle(ctx, uri)
            if (item == null || !item.isPkg) {
                say("Só arquivos .pkg podem ser enviados ao PS4")
                return@launch
            }
            val iconDir = File(ctx.cacheDir, "icons")
            iconDir.mkdirs()
            val info = PkgParser.parse(ctx, uri, iconDir, sha1("${item.uri}|${item.size}|${item.modified}"))
            pkgInfo.update { it + (item.uri to info) }
            sendFiles(listOf(item))
        }
    }

    fun removeFolder(folder: LibraryFolder) {
        try {
            ctx.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // já liberada
        }
        val list = folders.value.filter { it.uri != folder.uri }
        folders.value = list
        store.saveFolders(list)
        rescan()
    }

    fun rescan() {
        if (scanning.value) {
            rescanAgain = true
            return
        }
        scanning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = ArrayList<FileItem>()
                // Com "todo o celular" ativo e permitido, as outras pastas seriam só duplicatas dos mesmos arquivos.
                val allStorage = folders.value.filter { it.uri == StoragePermission.ALL_STORAGE_URI }
                val sources = if (allStorage.isNotEmpty() && StoragePermission.hasAllFiles(ctx)) allStorage else folders.value
                val problems = HashMap<String, String>()
                for (f in sources) {
                    val problem = FileScanner.checkFolder(ctx, Uri.parse(f.uri))
                    if (problem != null) {
                        problems[f.uri] = "${f.name}: $problem"
                        continue
                    }
                    all.addAll(FileScanner.scanTree(ctx, Uri.parse(f.uri)))
                }
                folderProblems.value = problems
                // PKG importados um a um: se o arquivo sumiu ou perdeu a permissão, sai da lista e avisa.
                val stillThere = ArrayList<String>()
                for (u in imported.value) {
                    val it = FileScanner.statSingle(ctx, Uri.parse(u))
                    if (it == null) {
                        say("Arquivo importado não está mais disponível e foi removido da lista")
                    } else {
                        stillThere.add(u)
                        if (all.none { x -> x.uri == it.uri }) all.add(it)
                    }
                }
                if (stillThere.size != imported.value.size) {
                    imported.value = stillThere
                    store.saveImported(stillThere)
                }
                files.value = all

                val uris = HashSet<String>()
                for (item in all) uris.add(item.uri)
                selection.update { s -> s.filterTo(HashSet()) { it in uris } }

                val infos = HashMap<String, PkgInfo>(pkgInfo.value)
                val iconDir = File(ctx.cacheDir, "icons")
                iconDir.mkdirs()
                var n = 0
                for (item in all) {
                    if (!item.isPkg) continue
                    val key = "${item.uri}|${item.size}|${item.modified}"
                    val cached = pkgCache[key]
                    val info: PkgInfo =
                        if (cached != null && (cached.iconPath == null || File(cached.iconPath).exists())) {
                            cached
                        } else {
                            val parsed = PkgParser.parse(ctx, Uri.parse(item.uri), iconDir, sha1(key))
                            pkgCache[key] = parsed
                            parsed
                        }
                    infos[item.uri] = info
                    n++
                    if (n % 4 == 0) pkgInfo.value = HashMap(infos)
                }
                pkgInfo.value = HashMap(infos)
                store.savePkgCache(pkgCache)
            } catch (e: Exception) {
                say("Erro ao ler as pastas: ${e.message}")
            } finally {
                scanning.value = false
                if (rescanAgain) {
                    rescanAgain = false
                    rescan()
                }
            }
        }
    }

    // ---------------- Consoles ----------------

    /** Retorna uma mensagem de erro, ou null se salvou. */
    fun saveConsole(name: String, ip: String, port: Int, method: String): String? {
        val cleanIp = ip.trim()
        val ipOk = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$").matches(cleanIp) &&
            cleanIp.split('.').all { it.toInt() in 0..255 }
        if (!ipOk) return "Endereço IP inválido (exemplo: 192.168.0.25)"
        if (port !in 1..65535) return "Porta inválida"
        val c = PsConsole(
            id = UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { "PS4" },
            ip = cleanIp,
            port = port,
            method = method,
        )
        val list = consoles.value + c
        consoles.value = list
        store.saveConsoles(list)
        if (selectedConsoleId.value == null) selectConsole(c.id)
        return null
    }

    fun deleteConsole(c: PsConsole) {
        val list = consoles.value.filter { it.id != c.id }
        consoles.value = list
        store.saveConsoles(list)
        if (selectedConsoleId.value == c.id) selectConsole(list.firstOrNull()?.id)
        testResults.update { it - c.id }
    }

    fun selectConsole(id: String?) {
        selectedConsoleId.value = id
        store.setSelectedConsole(id)
    }

    fun setConsoleMethod(c: PsConsole, method: String) {
        val list = consoles.value.map { if (it.id == c.id) it.copy(method = method) else it }
        consoles.value = list
        store.saveConsoles(list)
    }

    fun testConsole(c: PsConsole) {
        if (c.id in testing.value) return
        testing.update { it + c.id }
        viewModelScope.launch(Dispatchers.IO) {
            val lines = ArrayList<String>()
            var ok = false
            try {
                // Mesmas checagens que o DirectPackageInstaller usa para escolher o método.
                val rpi = Ps4Client.isRpiOnline(c.ip, c.port)
                val goldHen = Ps4Client.isGoldHenReady(c.ip)
                ok = rpi || goldHen

                if (rpi) {
                    lines.add("Remote Package Installer detectado na porta ${c.port}: método API remota disponível.")
                } else {
                    val api = Ps4Client.checkPort(c.ip, c.port)
                    lines.add(
                        if (api.open) {
                            "A porta ${c.port} abre, mas não reconheci o Remote Package Installer. Deixe o app aberto na tela do PS4."
                        } else {
                            "Remote Package Installer não detectado na porta ${c.port} (normal se o app não estiver aberto no PS4)."
                        }
                    )
                }
                if (goldHen) {
                    lines.add("Servidor de payloads do GoldHEN pronto (porta 9090): método payload disponível, sem precisar do app.")
                } else {
                    lines.add(
                        "Servidor de payloads do GoldHEN não respondeu em 9090 (ative-o nas configurações do GoldHEN, " +
                            "se quiser o método payload)."
                    )
                }
                if (!ok) {
                    lines.add("Nenhum método disponível agora. Confira o IP e se celular e PS4 estão na mesma rede.")
                } else if (c.method == "auto") {
                    lines.add("Modo automático: " + (if (rpi) "vai usar a API remota (RPI)." else "vai usar o payload do GoldHEN."))
                }
            } catch (e: Exception) {
                lines.add("Erro no teste: ${e.message}")
            }
            testResults.update { it + (c.id to TestResult(ok, lines, System.currentTimeMillis())) }
            testing.update { it - c.id }
        }
    }

    // ---------------- Transferências ----------------

    fun sendFiles(items: List<FileItem>) {
        val c = consoles.value.firstOrNull { it.id == selectedConsoleId.value }
        if (c == null) {
            say("Cadastre e selecione um console na aba Console")
            return
        }
        val pkgs = items.filter { it.isPkg }
        if (pkgs.isEmpty()) {
            say("Nenhum arquivo PKG selecionado")
            return
        }
        TransferManager.enqueue(ctx, pkgs, c, serverPort.value, pkgInfo.value)
        say("Envio iniciado: acompanhe na aba Envios")
    }

    fun cancelTransfer(id: String) = TransferManager.cancel(id)

    fun clearHistory() = TransferManager.clearHistory()

    // ---------------- Gerenciador de arquivos ----------------

    fun toggleSelection(uri: String) {
        selection.update { if (uri in it) it - uri else it + uri }
    }

    fun selectAll(uris: Collection<String>) {
        selection.value = uris.toHashSet()
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun deleteSelected() {
        val sel = selection.value
        val targets = files.value.filter { it.uri in sel }
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = HashSet<String>()
            var failed = 0
            for (item in targets) {
                if (FileScanner.delete(ctx, Uri.parse(item.uri))) deleted.add(item.uri) else failed++
            }
            files.update { list -> list.filter { it.uri !in deleted } }
            selection.value = emptySet()
            say(
                if (failed == 0) "${deleted.size} arquivo(s) excluído(s)"
                else "${deleted.size} excluído(s), $failed falharam"
            )
        }
    }

    fun moveSelected(folderName: String) {
        val name = folderName.trim().replace('/', '-')
        if (name.isEmpty()) {
            say("Informe o nome da pasta")
            return
        }
        val sel = selection.value
        val targets = files.value.filter { it.uri in sel }
        viewModelScope.launch(Dispatchers.IO) {
            val dirs = HashMap<String, Uri?>()
            var moved = 0
            var failed = 0
            for (item in targets) {
                val dir = dirs.getOrPut(item.rootUri) { FileScanner.findOrCreateDir(ctx, Uri.parse(item.rootUri), name) }
                if (dir == null) {
                    failed++
                    continue
                }
                if (FileScanner.move(ctx, item, dir)) moved++ else failed++
            }
            selection.value = emptySet()
            say(
                if (failed == 0) "$moved arquivo(s) movido(s) para \"$name\""
                else "$moved movido(s), $failed falharam (o provedor de armazenamento pode não permitir mover)"
            )
            scanning.value = false
            rescan()
        }
    }

    // ---------------- Configurações ----------------

    fun setAccent(key: String) {
        accent.value = key
        store.setAccent(key)
    }

    fun setGridView(v: Boolean) {
        gridView.value = v
        store.setGridView(v)
    }

    fun setServerPort(p: Int) {
        if (p !in 1024..65535) {
            say("Use uma porta entre 1024 e 65535")
            return
        }
        serverPort.value = p
        store.setServerPort(p)
        say("Porta salva. Vale a partir do próximo envio.")
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(ctx.cacheDir, "icons").deleteRecursively()
            } catch (e: Exception) {
            }
            store.deletePkgCache()
            pkgCache.clear()
            pkgInfo.value = emptyMap()
            say("Cache limpo")
            rescan()
        }
    }
}
