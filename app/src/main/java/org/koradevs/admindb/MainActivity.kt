package org.koradevs.admindb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koradevs.admindb.runtime.KoraWebViewActivity
import org.koradevs.admindb.store.KoraAppItem
import org.koradevs.admindb.store.KoraAppType
import org.koradevs.admindb.ui.*
import org.koradevs.admindb.ui.theme.KoraAdminDBTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoraAdminDBTheme {
                KoraMainAppContent()
            }
        }
    }
}

@Composable
fun KoraMainAppContent() {
    val stateHolder = rememberKoraStoreState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBarItem(
                    icon = { Text("🌟", fontSize = 20.sp) },
                    label = { Text("Tienda", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    selected = stateHolder.currentNavigation == KoraDestination.STORE,
                    onClick = { stateHolder.setNavigation(KoraDestination.STORE) }
                )
                NavigationBarItem(
                    icon = { Text("🛠️", fontSize = 20.sp) },
                    label = { Text("Inspector", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    selected = stateHolder.currentNavigation == KoraDestination.DATABASE_ADMIN,
                    onClick = { stateHolder.setNavigation(KoraDestination.DATABASE_ADMIN) }
                )
                NavigationBarItem(
                    icon = { Text("🤝", fontSize = 20.sp) },
                    label = { Text("P2P Sync", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    selected = stateHolder.currentNavigation == KoraDestination.P2P_SYNC,
                    onClick = { stateHolder.setNavigation(KoraDestination.P2P_SYNC) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (stateHolder.currentNavigation) {
                KoraDestination.STORE -> StoreHubScreen(stateHolder)
                KoraDestination.DATABASE_CONSOLE,
                KoraDestination.DATABASE_ADMIN -> DatabaseAdminScreen()
                KoraDestination.P2P_SYNC -> P2pSyncScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreHubScreen(stateHolder: KoraStoreStateHolder) {
    val context = LocalContext.current

    val filteredApps = remember(stateHolder.apps, stateHolder.searchQuery, stateHolder.selectedCategory) {
        stateHolder.apps.filter { app ->
            val matchesSearch = app.name.contains(stateHolder.searchQuery, ignoreCase = true) ||
                    app.description.contains(stateHolder.searchQuery, ignoreCase = true)
            val matchesCategory = when (stateHolder.selectedCategory) {
                "WEB" -> app.type == KoraAppType.WEB_APP
                "APK" -> app.type == KoraAppType.NATIVE_APK
                else -> true
            }
            matchesSearch && matchesCategory
        }
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
                Text(
                    text = "🎨 Kora Tienda Educativa",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Toca cualquier aplicación para abrirla y escuchar voz asistida",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { stateHolder.loadCatalog() }) {
                Text("🔄", fontSize = 22.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = stateHolder.searchQuery,
            onValueChange = { stateHolder.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("🔍 Buscar juegos, lectura, números...") },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = stateHolder.selectedCategory == "ALL",
                onClick = { stateHolder.updateSelectedCategory("ALL") },
                label = { Text("🌈 Todas") }
            )
            FilterChip(
                selected = stateHolder.selectedCategory == "WEB",
                onClick = { stateHolder.updateSelectedCategory("WEB") },
                label = { Text("🌐 Web Apps") }
            )
            FilterChip(
                selected = stateHolder.selectedCategory == "APK",
                onClick = { stateHolder.updateSelectedCategory("APK") },
                label = { Text("📱 Juegos APK") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (stateHolder.isLoadingCatalog) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (stateHolder.operationStatus.isNotEmpty()) {
            Text(
                text = stateHolder.operationStatus,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!stateHolder.isLoadingCatalog && filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎈 No hay aplicaciones disponibles en este momento.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (stateHolder.searchQuery.isEmpty() && stateHolder.selectedCategory == "ALL") {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "🌟 ¡Bienvenido al Ecosistema Kora!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Aplicaciones interactivas con voz y base de datos local para aprender sin barreras.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                items(filteredApps) { app ->
                    val isInstalled = remember(app.packageName) { stateHolder.isAppInstalled(app.packageName) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Versión ${app.version}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Badge(
                                    containerColor = if (app.type == KoraAppType.WEB_APP) Color(0xFF00796B) else Color(0xFFE65100)
                                ) {
                                    Text(
                                        text = if (app.type == KoraAppType.WEB_APP) "🌐 WEB" else "📱 APK",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = app.description,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (app.type == KoraAppType.WEB_APP) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, KoraWebViewActivity::class.java).apply {
                                            putExtra(KoraWebViewActivity.EXTRA_URL, app.sourceUrl)
                                            putExtra(KoraWebViewActivity.EXTRA_APP_NAME, app.name)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Text("🚀 ¡Abrir y Jugar Ahora!", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        stateHolder.downloadAndInstall(app)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInstalled) Color(0xFF455A64) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = if (isInstalled) "🔄 Reinstalar / Actualizar" else "📥 Descargar e Instalar",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
