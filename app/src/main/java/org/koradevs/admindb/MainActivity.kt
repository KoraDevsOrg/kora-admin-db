package org.koradevs.admindb

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.koradevs.admindb.db.KoraDbOpenHelper
import org.koradevs.admindb.runtime.KoraWebViewActivity
import org.koradevs.admindb.store.KoraAppItem
import org.koradevs.admindb.store.KoraAppType
import org.koradevs.admindb.store.KoraStoreManager
import org.koradevs.admindb.ui.theme.KoraAdminDBTheme

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: KoraDbOpenHelper
    private lateinit var storeManager: KoraStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = KoraDbOpenHelper(this)
        storeManager = KoraStoreManager(this)

        setContent {
            KoraAdminDBTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KoraMainScreen(dbHelper, storeManager)
                }
            }
        }
    }
}

@Composable
fun KoraMainScreen(dbHelper: KoraDbOpenHelper, storeManager: KoraStoreManager) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Motor DB", "Tienda Kora")

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Kora Admin DB",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Gestor Local-First & Ecosistema Hub",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                DatabaseTabContent(dbHelper)
            } else {
                StoreTabContent(storeManager)
            }
        }
    }
}

@Composable
fun DatabaseTabContent(dbHelper: KoraDbOpenHelper) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tablesList by remember { mutableStateOf(listOf<String>()) }
    var statusMessage by remember { mutableStateOf("") }

    val reloadTables = {
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
        tablesList = list
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadTables()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Estado del Almacenamiento", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "• Modo: WAL (Write-Ahead Logging)")
                Text(text = "• Tablas activas: ${tablesList.size}")
                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "• $statusMessage", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val intent = Intent(context, KoraWebViewActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Visor Web")
            }

            OutlinedButton(
                onClick = {
                    val db = dbHelper.writableDatabase
                    db.execSQL("VACUUM")
                    statusMessage = "Base de datos optimizada (VACUUM completado)"
                    Toast.makeText(context, "Disco compactado exitosamente", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Optimizar DB")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Tablas del Núcleo y Módulos:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tablesList) { tableName ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = tableName, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (tableName.startsWith("core_")) "Núcleo" else "Módulo ERP",
                            color = if (tableName.startsWith("core_")) Color(0xFF00796B) else Color(0xFF1976D2),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoreTabContent(storeManager: KoraStoreManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var appsList by remember { mutableStateOf<List<KoraAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var operationStatus by remember { mutableStateOf("") }

    // Función para refrescar desde GitHub o Caché
    val loadCatalog: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            appsList = storeManager.fetchCatalog()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCatalog()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Ecosistema Koradevs", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "Apps conectadas al motor local.", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = { loadCatalog() }) {
                Text("🔄", fontSize = 20.sp)
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (operationStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = operationStatus, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isLoading && appsList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se pudo cargar el catálogo. Verifica tu conexión.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(appsList) { app ->
                    val isInstalled = remember(app.packageName) { storeManager.isAppInstalled(app.packageName) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = app.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Badge(
                                    containerColor = if (app.type == KoraAppType.WEB_APP) Color(0xFF00796B) else Color(0xFFE65100)
                                ) {
                                    Text(if (app.type == KoraAppType.WEB_APP) "WEB" else "APK", color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = app.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))

                            if (app.type == KoraAppType.WEB_APP) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, KoraWebViewActivity::class.java).apply {
                                            putExtra(KoraWebViewActivity.EXTRA_URL, app.sourceUrl)
                                            putExtra(KoraWebViewActivity.EXTRA_APP_NAME, app.name)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Abrir Aplicación")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            storeManager.downloadAndInstallApk(app.sourceUrl, app.id) { status ->
                                                operationStatus = status
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInstalled) Color(0xFF455A64) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (isInstalled) "Reinstalar / Actualizar APK" else "Descargar e Instalar APK")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}