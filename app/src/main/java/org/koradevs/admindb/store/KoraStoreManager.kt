package org.koradevs.admindb.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class KoraStoreManager(private val context: Context) {

    /**
     * Catálogo integrado del ecosistema.
     * Más adelante puede leerse de un archivo JSON alojado en GitHub.
     */
    fun getAvailableApps(): List<KoraAppItem> {
        return listOf(
            KoraAppItem(
                id = "jp_web",
                name = "Kora Japonés (Web)",
                description = "Aprende Kanji, Kana y vocabulario sin anuncios.",
                type = KoraAppType.WEB_APP,
                packageName = "org.koradevs.japon.web",
                version = "1.0.0",
                versionCode = 1,
                sourceUrl = "https://koradevs.local"
            ),
            KoraAppItem(
                id = "fin_apk",
                name = "Kora Finanzas (APK)",
                description = "Control de ingresos y balances con persistencia local.",
                type = KoraAppType.NATIVE_APK,
                packageName = "org.koradevs.finanzas",
                version = "1.0.0",
                versionCode = 1,
                sourceUrl = "https://github.com/koradevsorg/kora-finanzas/releases/download/v1.0.0/app-release.apk"
            )
        )
    }

    /**
     * Verifica si un APK ya está instalado en el teléfono
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Descarga el APK desde la URL e invoca el instalador nativo de Android
     */
    suspend fun downloadAndInstallApk(apkUrl: String, fileName: String, onProgress: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                onProgress("Conectando con GitHub...")
                val url = URL(apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val apkDir = File(context.cacheDir, "apks")
                if (!apkDir.exists()) apkDir.mkdirs()
                val apkFile = File(apkDir, "$fileName.apk")

                onProgress("Descargando paquete...")
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }

                onProgress("Lanzando instalador...")
                withContext(Dispatchers.Main) {
                    val apkUri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        apkFile
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onProgress("Instalador abierto")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onProgress("Error: ${e.localizedMessage}")
                }
            }
        }
    }
}