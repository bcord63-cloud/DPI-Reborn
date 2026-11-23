package com.dpireborn.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

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

    /** Procura uma subpasta com esse nome na raiz da árvore; cria se não existir. */
    fun findOrCreateDir(ctx: Context, tree: Uri, name: String): Uri? {
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
        DocumentsContract.deleteDocument(ctx.contentResolver, uri)
    } catch (e: Exception) {
        false
    }

    fun move(ctx: Context, item: FileItem, targetDir: Uri): Boolean = try {
        DocumentsContract.moveDocument(
            ctx.contentResolver,
            Uri.parse(item.uri),
            Uri.parse(item.parentUri),
            targetDir,
        ) != null
    } catch (e: Exception) {
        false
    }
}
