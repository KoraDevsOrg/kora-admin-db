package org.koradevs.admindb.ui

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koradevs.admindb.db.DataScope
import org.koradevs.admindb.db.DatabaseInspectorHelper
import org.koradevs.admindb.db.KoraDbOpenHelper
import org.koradevs.admindb.store.GitSyncManager

@Composable
fun DatabaseAdminScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dbHelper = remember { KoraDbOpenHelper(context) }
    val inspector = remember { DatabaseInspectorHelper(dbHelper) }
    val gitManager = remember { GitSyncManager(context) }

    var tables by remember { mutableStateOf(inspector.getAllTables()) }
    var statusMessage by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    val reload = {
        tables = inspector.getAllTables()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🛠️ Inspector & Administrador DB",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Local-First • WAL • PK/FK • Scopes Seguros",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Botones de Mantenimiento y Git Sync
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isRefreshing = true
                        val res = withContext(Dispatchers.IO) { inspector.runVacuumAndIntegrityCheck() }
                        statusMessage = res
                        reload()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("⚡ VACUUM & Check")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isRefreshing = true
                        statusMessage = "Sincronizando con GitHub..."
                        val result = gitManager.forceResync()
                        statusMessage = if (result.isSuccess) {
                            "¡Git Sync exitoso! Apps: ${result.getOrNull()}"
                        } else {
                            "Error Git Sync: ${result.exceptionOrNull()?.localizedMessage}"
                        }
                        isRefreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
            ) {
                Text("🔄 Re-sync Git")
            }
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Estructura y Esquema de Tablas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (tables.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No hay tablas en la base de datos.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tables) { table ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (table.scope == DataScope.LOCAL_SCOPE)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = table.tableName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Badge(
                                    containerColor = if (table.scope == DataScope.LOCAL_SCOPE) Color(0xFFC62828) else Color(0xFF2E7D32)
                                ) {
                                    Text(
                                        text = if (table.scope == DataScope.LOCAL_SCOPE) "🔒 LOCAL_SCOPE" else "🤝 SHARED_SCOPE",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Registros: ${table.rowCount} • Columnas: ${table.columns.size}", fontSize = 13.sp)

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PKs: " + table.columns.filter { it.isPrimaryKey }.joinToString { it.name },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (table.foreignKeys.isNotEmpty()) {
                                Text(
                                    text = "FKs: " + table.foreignKeys.joinToString { "${it.column} ➔ ${it.parentTable}(${it.parentColumn})" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        inspector.purgeTableData(table.tableName)
                                        reload()
                                        statusMessage = "Purgado datos de ${table.tableName}"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🧹 Vaciar Tabla")
                                }

                                if (table.scope == DataScope.SHARED_SCOPE) {
                                    OutlinedButton(
                                        onClick = {
                                            inspector.dropOrphanTable(table.tableName)
                                            reload()
                                            statusMessage = "Tabla ${table.tableName} eliminada"
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                                    ) {
                                        Text("🗑️ Drop Módulo")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
