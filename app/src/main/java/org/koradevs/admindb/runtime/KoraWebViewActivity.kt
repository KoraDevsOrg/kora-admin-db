package org.koradevs.admindb.runtime

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.koradevs.admindb.db.KoraDbOpenHelper

class KoraWebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var dbHelper: KoraDbOpenHelper

    companion object {
        const val EXTRA_URL = "extra_target_url"
        const val EXTRA_APP_NAME = "extra_app_name"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = KoraDbOpenHelper(this)

        // Solicitar permisos de audio (micrófono) si no están concedidos
        checkAndRequestAudioPermissions()

        val targetUrl = intent.getStringExtra(EXTRA_URL) ?: "local://demo"

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false // Permite reproducción de audio/video sin gesto de usuario

            // Mantiene el puente nativo hacia SQLite en cualquier app web cargada
            addJavascriptInterface(KoraWebBridge(dbHelper) {}, "KoraDB")

            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Concede automáticamente permisos solicitados por la Web App (como micrófono / audio)
                    request?.resources?.let { resources ->
                        request.grant(resources)
                    }
                }

                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    Log.d("KORA_JS_LOG", "${message?.message()} -- Line: ${message?.lineNumber()}")
                    return true
                }
            }
        }

        setContentView(webView)

        if (targetUrl == "local://demo") {
            cargarDemoInterno()
        } else {
            // Carga la app remota de GitHub Pages
            webView.loadUrl(targetUrl)
        }
    }

    private fun checkAndRequestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun cargarDemoInterno() {
        val demoHtml = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Kora Web Demo & Audio Sandbox</title>
                <style>
                    body { font-family: sans-serif; padding: 16px; background: #121212; color: #fff; }
                    button { background: #2E7D32; color: white; border: none; padding: 14px; border-radius: 8px; width: 100%; margin: 6px 0; font-size: 15px; font-weight: bold; cursor: pointer; }
                    button:active { background: #1B5E20; }
                    pre { background: #1e1e1e; padding: 12px; border-radius: 6px; overflow-x: auto; color: #81C784; font-size: 13px; max-height: 250px; }
                    .error { color: #E57373 !important; }
                </style>
            </head>
            <body>
                <h3>Consola Web Local Kora & Audio</h3>
                <button onclick="probarMicrofono()">🎙️ Probar Micrófono (MediaRecorder)</button>
                <button onclick="crearModulo()">1. Registrar Módulo y Tablas</button>
                <button onclick="insertarPalabra()">2. Insertar Registro</button>
                <button onclick="consultar()">3. Consultar Registros</button>

                <p style="margin-top:16px; color:#aaa; font-size:12px;>Salida:</p>
                <pre id="output">Esperando acción...</pre>

                <script>
                    function mostrar(data, esError = false) {
                        const out = document.getElementById('output');
                        out.className = esError ? 'error' : '';
                        out.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
                    }

                    async function probarMicrofono() {
                        try {
                            mostrar("Solicitando acceso al micrófono...");
                            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                            mostrar("¡Micrófono concedido con éxito! Tracks activos: " + stream.getAudioTracks().length);
                        } catch(e) {
                            mostrar("Error de Micrófono: " + e.message, true);
                        }
                    }

                    function crearModulo() {
                        try {
                            const ddl = "CREATE TABLE IF NOT EXISTS mod_jp_palabras (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT, kana TEXT, significado TEXT);";
                            const res = window.KoraDB.registerModule("org.koradevs.japon.web", "Kora Japonés Web", 1, ddl);
                            mostrar(JSON.parse(res));
                        } catch(e) {
                            mostrar("Error JS: " + e.message, true);
                        }
                    }

                    function insertarPalabra() {
                        try {
                            const res = window.KoraDB.execute(
                                "INSERT INTO mod_jp_palabras (kanji, kana, significado) VALUES (?, ?, ?)",
                                JSON.stringify(["日本語", "にほんご", "Idioma japonés"])
                            );
                            mostrar(JSON.parse(res));
                        } catch(e) {
                            mostrar("Error JS: " + e.message, true);
                        }
                    }

                    function consultar() {
                        try {
                            const res = window.KoraDB.query("SELECT * FROM mod_jp_palabras");
                            mostrar(JSON.parse(res));
                        } catch(e) {
                            mostrar("Error JS: " + e.message, true);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://koradevs.local", demoHtml, "text/html", "UTF-8", null)
    }
}
