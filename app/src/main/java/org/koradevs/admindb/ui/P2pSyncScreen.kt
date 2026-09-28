package org.koradevs.admindb.ui

import androidx.compose.foundation.layout.*
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
import org.koradevs.admindb.db.KoraDbOpenHelper
import org.koradevs.admindb.db.KoraP2pSyncEngine

@Composable
fun P2pSyncScreen() {
    val context = LocalContext.current
    val dbHelper = remember { KoraDbOpenHelper(context) }
    val syncEngine = remember { KoraP2pSyncEngine(dbHelper) }

    var exportJsonPayload by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    var syncStatusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "🤝 P2P Friend Sync (Distributed ERP)",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Sincronización ad-hoc entre amigos • SHARED_SCOPE únicamente (Privacidad estricta en LOCAL_SCOPE)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "1️⃣ Exportar Datos Compartidos (Enviar)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Genera un paquete JSON excluyendo contraseñas, saldos y llaves privadas (core_*).", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        exportJsonPayload = syncEngine.exportSharedPayload()
                        syncStatusMessage = "Paquete P2P exportado correctamente."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📤 Generar Paquete JSON P2P")
                }

                if (exportJsonPayload.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportJsonPayload,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        readOnly = true,
                        label = { Text("Payload JSON para QR / Wi-Fi Direct") }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "2️⃣ Importar Paquete de Amigo (Recibir)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Pega aquí el JSON recibido de tu amigo para fusionar catálogos y módulos.", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = importJsonInput,
                    onValueChange = { importJsonInput = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("Pega el JSON de sincronización aquí...") }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (importJsonInput.isBlank()) {
                            syncStatusMessage = "El JSON está vacío."
                        } else {
                            val result = syncEngine.importSharedPayload(importJsonInput)
                            syncStatusMessage = if (result.isSuccess) {
                                result.getOrNull() ?: "Sincronización P2P completada"
                            } else {
                                "Error en P2P Sync: ${result.exceptionOrNull()?.localizedMessage}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                ) {
                    Text("📥 Sincronizar y Fusionar con DB Local")
                }
            }
        }

        if (syncStatusMessage.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = syncStatusMessage,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
