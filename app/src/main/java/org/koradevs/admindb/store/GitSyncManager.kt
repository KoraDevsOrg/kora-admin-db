package org.koradevs.admindb.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GitSyncManager(private val context: Context) {

    companion object {
        private const val CATALOG_URL = "https://raw.githubusercontent.com/KoraDevsOrg/kora-admin-db/main/kora-catalog.json"
        private const val CACHE_FILE_NAME = "kora_catalog_cache.json"
    }

    /**
     * Fuerza una resincronización manual contra GitHub para obtener el último catálogo de apps y módulos.
     */
    suspend fun forceResync(): Result<Int> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        try {
            val url = URL(CATALOG_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                cacheFile.writeText(jsonString)
                val jsonArray = JSONArray(jsonString)
                Result.success(jsonArray.length())
            } else {
                Result.failure(Exception("Error HTTP: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            // Si falla la red, verificamos si hay caché local
            if (cacheFile.exists()) {
                val cachedJson = cacheFile.readText()
                val count = JSONArray(cachedJson).length()
                Result.success(count)
            } else {
                Result.failure(e)
            }
        }
    }
}
