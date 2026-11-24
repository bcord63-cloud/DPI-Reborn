package com.dpireborn.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.os.Environment
import java.io.File

/** Varredura de pastas escolhidas pelo Storage Access Framework (sem permissão ampla de armazenamento). */
object FileScanner {
    private const val MAX_DEPTH = 8
    private const val MAX_FILES = 30000

    private val PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_MIME_TYPE,
    )

    fun scanTree(ctx: Context, tree: Uri): List<FileItem> {
        if (tree.scheme == "allfiles") return scanAllStorage(ctx)
        if (tree.scheme == "file") {
            val res = ArrayList<FileItem>()
            if (StoragePermission.hasAllFiles(ctx)) walkFiles(File(tree.path ?: ""), 0, res, tree.toString())
            return res
        }
        val out = ArrayList<FileItem>()
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: Exception) {
            return out
        }
        walk(ctx.contentResolver, tree, rootId, 0, out)
        return out
    }

    private fun walk(cr: ContentResolver, tree: Uri, parentId: String, depth: Int, out: MutableList<FileItem>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId).toString()
        val dirs = ArrayList<String>()
        try {
            cr.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val size = if (c.isNull(2)) 0L else c.getLong(2)
                    val modified = if (c.isNull(3)) 0L else c.getLong(3)
                    val mime = c.getString(4) ?: ""
                    if (mime == Document.MIME_TYPE_DIR) {
                        dirs.add(id)
                    } else {
                        out.add(
                            FileItem(
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                parentUri = parentUri,
                                rootUri = tree.toString(),
                                name = name,
                                size = size,
                                modified = modified,
                                mime = mime,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // pasta inacessível: segue em frente
        }
        for (d in dirs) walk(cr, tree, d, depth + 1, out)
    }

    /**
     * Procura arquivos .pkg em todo o armazenamento (interno e cartões SD) por caminho de arquivo.
     * Exige "Acesso a todos os arquivos". Só devolve PKG, para não listar centenas de milhares de arquivos.
     */
    @Suppress("DEPRECATION")
    fun scanAllStorage(ctx: Context): List<FileItem> {
        val out = ArrayList<FileItem>()
        if (!StoragePermission.hasAllFiles(ctx)) return out
        val roots = ArrayList<File>()
        roots.add(Environment.getExternalStorageDirectory())
        try {
            File("/storage").listFiles()?.forEach {
                if (it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead()) roots.add(it)
            }
        } catch (e: Exception) {
            // sem cartão SD ou volume ilegível
        }
        for (r in roots) walkFiles(r, 0, out)
        return out
    }

    private fun walkFiles(dir: File, depth: Int, out: MutableList<FileItem>, rootUri: String = StoragePermission.ALL_STORAGE_URI) {
        if (depth > 12 || out.size >= MAX_FILES) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return
        for (f in children) {
            try {
                if (f.isDirectory) {
                    val n = f.name
                    if (n.startsWith(".")) continue
                    if (depth == 0 && n == "Android") continue // dados de apps: o Android não deixa ler
                    walkFiles(f, depth + 1, out, rootUri)
                } else if (f.name.endsWith(".pkg", ignoreCase = true)) {
                    out.add(
                        FileItem(
                            uri = Uri.fromFile(f).toString(),
                            parentUri = Uri.fromFile(dir).toString(),
                            rootUri = rootUri,
                            name = f.name,
                            size = f.length(),
                            modified = f.lastModified(),
                            mime = "application/octet-stream",
                        )
                    )
                }
            } catch (e: Exception) {
                // item ilegível: segue
            }
        }
    }

    /** Procura uma subpasta com esse nome na raiz da árvore; cria se não existir. */
    fun findOrCreateDir(ctx: Context, tree: Uri, name: String): Uri? {
        if (tree.scheme == "file") {
            val d = File(tree.path ?: return null, name)
            return if (d.isDirectory || d.mkdirs()) Uri.fromFile(d) else null
        }
        if (tree.scheme == "allfiles") {
            @Suppress("DEPRECATION")
            val d = File(Environment.getExternalStorageDirectory(), name)
            return if (d.isDirectory || d.mkdirs()) Uri.fromFile(d) else null
        }
        val cr = ctx.contentResolver
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: Exception) {
            return null
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        try {
            cr.query(
                children,
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(2) == Document.MIME_TYPE_DIR && c.getString(1) == name) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    }
                }
            }
        } catch (e: Exception) {
            // tenta criar
        }
        return try {
            DocumentsContract.createDocument(
                cr,
                DocumentsContract.buildDocumentUriUsingTree(tree, rootId),
                Document.MIME_TYPE_DIR,
                name,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, uri: Uri): Boolean = try {
        if (uri.scheme == "file") {
            File(uri.path ?: "").delete()
        } else {
            DocumentsContract.deleteDocument(ctx.contentResolver, uri)
        }
    } catch (e: Exception) {
        false
    }

    fun move(ctx: Context, item: FileItem, targetDir: Uri): Boolean = try {
        val src = Uri.parse(item.uri)
        if (src.scheme == "file") {
            val s = File(src.path ?: "")
            val t = File(targetDir.path ?: "", s.name)
            !t.exists() && s.renameTo(t)
        } else {
            DocumentsContract.moveDocument(
                ctx.contentResolver,
                src,
                Uri.parse(item.parentUri),
                targetDir,
            ) != null
        }
    } catch (e: Exception) {
        false
    }

    /** Lê nome, tamanho e data de um único documento (arquivo importado). Null se removido ou sem permissão. */
    fun statSingle(ctx: Context, uri: Uri): FileItem? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            if (!f.isFile) return null
            return FileItem(uri.toString(), uri.toString(), "imported", f.name, f.length(), f.lastModified(), "application/octet-stream")
        }
        return try {
            ctx.contentResolver.query(
                uri,
                arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) {
                    null
                } else {
                    FileItem(
                        uri = uri.toString(),
                        parentUri = uri.toString(),
                        rootUri = "imported",
                        name = c.getString(0) ?: "arquivo.pkg",
                        size = if (c.isNull(1)) 0L else c.getLong(1),
                        modified = if (c.isNull(2)) 0L else c.getLong(2),
                        mime = "application/octet-stream",
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Devolve uma mensagem de problema para a pasta (permissão revogada, pasta indisponível) ou null se está tudo certo. */
    fun checkFolder(ctx: Context, tree: Uri): String? {
        if (tree.scheme == "file") {
            if (!StoragePermission.hasAllFiles(ctx)) return "Acesso a todos os arquivos foi desativado nos ajustes do Android"
            val d = File(tree.path ?: "")
            return if (d.isDirectory && d.canRead()) null else "Pasta indisponível ou removida"
        }
        if (tree.scheme == "allfiles") {
            return if (StoragePermission.hasAllFiles(ctx)) null else "Acesso a todos os arquivos foi desativado nos ajustes do Android"
        }
        val granted = ctx.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!granted) return "Permissão revogada: selecione a pasta novamente"
        return try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val c = ctx.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
                arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null,
            )
            if (c == null) "Pasta indisponível (o provedor não respondeu)" else { c.close(); null }
        } catch (e: Exception) {
            "Pasta indisponível ou removida"
        }
    }
}
