package com.dpireborn.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * "Acesso a todos os arquivos" (MANAGE_EXTERNAL_STORAGE). Necessário porque, a partir do Android 11,
 * o seletor de pastas não deixa escolher a pasta Download nem a raiz do armazenamento.
 */
object StoragePermission {
    /** Pasta virtual da biblioteca que significa "procurar PKG em todo o armazenamento". */
    const val ALL_STORAGE_URI = "allfiles://"

    fun hasAllFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Abre a tela de ajustes do Android onde o usuário liga "Permitir gerenciar todos os arquivos". */
    fun settingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
        }
}
