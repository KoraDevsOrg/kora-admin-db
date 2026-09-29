package org.koradevs.admindb.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
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

    // Estado para el visor visual de datos de una tabla seleccionada
    var selectedTableForView by remember { mutableStateOf<String?>(null) }

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
            text = "Local-First • Prioridad Local ante Git • Búsqueda Visual",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Botones de Mantenimiento y Git Sync (Respetando datos locales de usuario)
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
                        statusMessage = "Sincronizando catálogo con GitHub (respetando datos locales)..."
                        val result = gitManager.forceResync()
                        statusMessage = if (result.isSuccess) {
                            "¡Git Sync exitoso! Catálogo actualizado (datos de usuario intactos)."
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
                Text("🔄 Re-sync Git Safe")
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
        Text(text = "Tablas del Sistema (Toca para ver datos)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTableForView = table.tableName },
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

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { selectedTableForView = table.tableName },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🔍 Ver Datos y Buscar")
                                }

                                OutlinedButton(
                                    onClick = {
                                        inspector.purgeTableData(table.tableName)
                                        reload()
                                        statusMessage = "Purgado datos de ${table.tableName}"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🧹 Vaciar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo / Pantalla Visual de Datos de la Tabla Seleccionada con Búsqueda y Filtro
    selectedTableForView?.let { tableName ->
        TableDataViewerDialog(
            tableName = tableName,
            inspector = inspector,
            onDismiss = { selectedTableForView = null }
        )
    }
}

@Composable
fun TableDataViewerDialog(
    tableName: String,
    inspector: DatabaseInspectorHelper,
    onDismiss: () -> Unit
) {
    var rawRows by remember { mutableStateOf(inspector.getTableRows(tableName)) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredRows = remember(rawRows, searchQuery) {
        if (searchQuery.isBlank()) {
            rawRows
        } else {
            rawRows.filter { rowMap ->
                rowMap.values.any { value ->
                    value.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
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
                            text = "📋 Tabla: $tableName",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Total filas: ${rawRows.size} • Filtradas: ${filteredRows.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Barra de búsqueda y filtrado fácil
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("🔎 Buscar en cualquier columna...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredRows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No se encontraron registros.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRows) { rowMap ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    rowMap.forEach { (colName, colVal) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "$colName:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.width(110.dp)
                                            )
                                            Text(
                                                text = colVal,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
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
    }
}
