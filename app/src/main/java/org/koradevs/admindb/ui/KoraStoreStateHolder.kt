package org.koradevs.admindb.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koradevs.admindb.db.KoraDbOpenHelper
import org.koradevs.admindb.store.KoraAppItem
import org.koradevs.admindb.store.KoraStoreManager

enum class KoraDestination {
    STORE,
    DATABASE_CONSOLE
}

class KoraStoreStateHolder(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    var apps by mutableStateOf<List<KoraAppItem>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    var selectedCategory by mutableStateOf("ALL") // "ALL", "WEB", "APK"
        private set

    var isLoadingCatalog by mutableStateOf(false)
        private set

    var operationStatus by mutableStateOf("")
        private set

    var dbTables by mutableStateOf<List<String>>(emptyList())
        private set

    var dbStatusMessage by mutableStateOf("")
        private set

    var currentNavigation by mutableStateOf(KoraDestination.STORE)
        private set

    private val storeManager = KoraStoreManager(context)
    private val dbHelper = KoraDbOpenHelper(context)

    init {
        loadCatalog()
        reloadDbTables()
    }

    fun setNavigation(destination: KoraDestination) {
        currentNavigation = destination
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun updateSelectedCategory(category: String) {
        selectedCategory = category
    }

    fun loadCatalog() {
        coroutineScope.launch {
            isLoadingCatalog = true
            operationStatus = "Sincronizando con KoraDevsOrg..."
            val fetchedApps = withContext(Dispatchers.IO) {
                storeManager.fetchCatalog()
            }
            apps = fetchedApps
            isLoadingCatalog = false
            operationStatus = if (fetchedApps.isNotEmpty()) "Catálogo sincronizado correctamente" else "Sin conexión: Usando caché local"
        }
    }

    fun reloadDbTables() {
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
                null
            )
            val list = mutableListOf<String>()
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
            cursor.close()
            dbTables = list
        } catch (e: Exception) {
            dbStatusMessage = "Error cargando tablas: ${e.localizedMessage}"
        }
    }

    fun optimizeDatabase() {
        try {
            val db = dbHelper.writableDatabase
            db.execSQL("VACUUM")
            reloadDbTables()
            dbStatusMessage = "Base de datos optimizada (VACUUM completado)"
        } catch (e: Exception) {
            dbStatusMessage = "Error optimizando: ${e.localizedMessage}"
        }
    }

    fun downloadAndInstall(app: KoraAppItem) {
        coroutineScope.launch {
            storeManager.downloadAndInstallApk(app.sourceUrl, app.id) { status ->
                operationStatus = status
            }
        }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return storeManager.isAppInstalled(packageName)
    }
}

@Composable
fun rememberKoraStoreState(
    context: Context = LocalContext.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): KoraStoreStateHolder {
    return remember(context, coroutineScope) {
        KoraStoreStateHolder(context, coroutineScope)
    }
}
