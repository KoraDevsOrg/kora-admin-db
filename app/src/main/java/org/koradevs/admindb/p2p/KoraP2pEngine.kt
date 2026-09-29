package org.koradevs.admindb.p2p

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.koradevs.admindb.db.KoraDbOpenHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class KoraP2pEngine(
    private val context: Context,
    private val dbHelper: KoraDbOpenHelper,
    private val onSyncStatus: (String) -> Unit
) {
    private val mergeEngine = KoraMergeEngine(dbHelper)
    private val nsdDiscovery = KoraNsdDiscovery(context) { host, port ->
        // Al descubrir un par en la red local, iniciar conexión P2P saliente
        syncWithPeer(host, port)
    }

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        const val PORT = 8888
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        nsdDiscovery.registerService(PORT)
        nsdDiscovery.discoverPeers()
        startServer()
        onSyncStatus("Motor P2P LAN activo (Escuchando y buscando pares...)")
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        nsdDiscovery.stopDiscovery()
        scope.cancel()
        onSyncStatus("Motor P2P LAN detenido")
    }

    private fun startServer() {
        serverJob = scope.launch {
            try {
                val serverSocket = ServerSocket(PORT)
                while (isRunning && isActive) {
                    val socket = serverSocket.accept()
                    launch { handleIncomingConnection(socket) }
                }
            } catch (e: Exception) {
                Log.e("KoraP2pEngine", "Error en servidor TCP: ${e.localizedMessage}")
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val peerPayload = reader.readLine()
            if (!peerPayload.isNullOrBlank()) {
                val mergeResult = mergeEngine.mergeDeltas(peerPayload)
                if (mergeResult.isSuccess) {
                    onSyncStatus("📥 Sincronizado entrante: ${mergeResult.getOrNull()}")
                }
            }

            // Responder con nuestros propios deltas (Bidirectional Handshake)
            val ourPayload = mergeEngine.extractSharedDeltas()
            writer.println(ourPayload)

            socket.close()
        } catch (e: Exception) {
            Log.e("KoraP2pEngine", "Error manejando conexión entrante: ${e.localizedMessage}")
        }
    }

    fun syncWithPeer(host: String, port: Int) {
        scope.launch {
            try {
                val socket = Socket(host, port)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Enviar nuestros deltas
                val ourPayload = mergeEngine.extractSharedDeltas()
                writer.println(ourPayload)

                // Recibir respuesta del par
                val peerPayload = reader.readLine()
                if (!peerPayload.isNullOrBlank()) {
                    val mergeResult = mergeEngine.mergeDeltas(peerPayload)
                    if (mergeResult.isSuccess) {
                        onSyncStatus("📤 Sincronizado saliente con $host: ${mergeResult.getOrNull()}")
                    }
                }

                socket.close()
            } catch (e: Exception) {
                Log.e("KoraP2pEngine", "Error conectando con par $host:$port -> ${e.localizedMessage}")
            }
        }
    }
}
