package org.koradevs.admindb.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class KoraStoreManager(private val context: Context) {

    companion object {
        private const val CATALOG_URL = "https://raw.githubusercontent.com/KoraDevsOrg/kora-admin-db/main/kora-catalog.json"
        private const val CACHE_FILE_NAME = "kora_catalog_cache.json"
    }

    /**
     * Obtiene las aplicaciones disponibles:
     * 1. Si hay internet, descarga la última versión desde GitHub y actualiza la caché.
     * 2. Si no hay conexión o falla, recupera la última copia guardada en disco.
     */
    suspend fun fetchCatalog(): List<KoraAppItem> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        var jsonString: String? = null

        try {
            val url = URL(CATALOG_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                // Guardar en caché local para uso offline
                cacheFile.writeText(jsonString)
            }
        } catch (_: Exception) {
            // Error de red: recurrir a la caché local
        }

        // Si la red falló, intentar leer la caché persistida
        if (jsonString == null && cacheFile.exists()) {
            jsonString = cacheFile.readText()
        }

        if (!jsonString.isNullOrBlank()) {
            parseCatalogJson(jsonString)
        } else {
            emptyList()
        }
    }

    private fun parseCatalogJson(jsonString: String): List<KoraAppItem> {
        val items = mutableListOf<KoraAppItem>()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            items.add(
                KoraAppItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.getString("description"),
                    type = if (obj.getString("type") == "WEB_APP") KoraAppType.WEB_APP else KoraAppType.NATIVE_APK,
                    packageName = obj.getString("packageName"),
                    version = obj.getString("version"),
                    versionCode = obj.getInt("versionCode"),
                    sourceUrl = obj.getString("sourceUrl")
                )
            )
        }
        return items
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

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