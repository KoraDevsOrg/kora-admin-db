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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.koradevs.admindb.db.KoraDbOpenHelper

class KoraWebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var dbHelper: KoraDbOpenHelper
    private lateinit var ttsBridge: KoraTTSBridge

    companion object {
        const val EXTRA_URL = "extra_target_url"
        const val EXTRA_APP_NAME = "extra_app_name"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dbHelper = KoraDbOpenHelper(this)

        checkAndRequestAudioPermissions()

        val targetUrl = intent.getStringExtra(EXTRA_URL) ?: "local://demo"
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Mini App Kora"

        ttsBridge = KoraTTSBridge(this) { status ->
            Log.d("KORA_TTS", status)
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false

            // Puentes JavaScript nativos: SQLite (KoraDB) y Síntesis de Voz (KoraTTS)
            addJavascriptInterface(KoraWebBridge(dbHelper) {}, "KoraDB")
            addJavascriptInterface(ttsBridge, "KoraTTS")

            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
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

        // Aplicar insets de sistema para evitar solapamiento con barras de estado y navegación
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setContentView(webView)

        if (targetUrl == "local://demo") {
            cargarDemoInterno(appName)
        } else {
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

    override fun onDestroy() {
        super.onDestroy()
        ttsBridge.shutdown()
    }

    private fun cargarDemoInterno(appName: String) {
        val demoHtml = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
                <title>$appName</title>
                <style>
                    body { font-family: sans-serif; padding: env(safe-area-inset-top, 20px) 20px env(safe-area-inset-bottom, 20px) 20px; background: #FFF9C4; color: #333; text-align: center; }
                    h1 { color: #E65100; font-size: 28px; }
                    button { background: #4CAF50; color: white; border: none; padding: 18px 24px; border-radius: 16px; width: 100%; margin: 10px 0; font-size: 18px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    button:active { background: #388E3C; transform: scale(0.98); }
                    .tts-btn { background: #FF9800; }
                    pre { background: #FFF; padding: 14px; border-radius: 12px; overflow-x: auto; color: #2E7D32; font-size: 15px; text-align: left; border: 2px dashed #81C784; }
                </style>
            </head>
            <body>
                <h1>🌟 $appName 🌟</h1>
                <p style="font-size: 16px; font-weight: bold; color: #555;">Aplicación accesible con voz asistida para niños y adultos.</p>

                <button class="tts-btn" onclick="hablarTexto('Bienvenido a la aplicación educativa Kora. Toca los botones para interactuar.')">🔊 Escuchar Bienvenida</button>
                <button onclick="crearModulo()">1. Registrar Módulo y Base de Datos</button>
                <button onclick="insertarPalabra()">2. Guardar Registro Local</button>
                <button onclick="consultar()">3. Leer Registros Guardados</button>

                <p style="margin-top:20px; font-weight:bold;">Estado SQLite y Voz:</p>
                <pre id="output">Listo para interactuar...</pre>

                <script>
                    function hablarTexto(texto) {
                        try {
                            window.KoraTTS.speak(texto);
                        } catch(e) {
                            console.log("TTS Error: " + e.message);
                        }
                    }

                    function mostrar(data, esError = false) {
                        const out = document.getElementById('output');
                        const textStr = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
                        out.textContent = textStr;
                        hablarTexto(textStr);
                    }

                    function crearModulo() {
                        try {
                            val ddl = "CREATE TABLE IF NOT EXISTS mod_aprender (id INTEGER PRIMARY KEY AUTOINCREMENT, palabra TEXT, significado TEXT);";
                            const res = window.KoraDB.registerModule("org.koradevs.aprender", "Aprender Jugando", 1, ddl);
                            mostrar("Módulo registrado correctamente en el núcleo.");
                        } catch(e) {
                            mostrar("Error al registrar módulo: " + e.message);
                        }
                    }

                    function insertarPalabra() {
                        try {
                            window.KoraDB.execute(
                                "INSERT INTO mod_aprender (palabra, significado) VALUES (?, ?)",
                                JSON.stringify(["Manzana", "Fruta roja y dulce para comer"])
                            );
                            mostrar("¡Guardado exitoso! Palabra: Manzana.");
                        } catch(e) {
                            mostrar("Error al insertar: " + e.message);
                        }
                    }

                    function consultar() {
                        try {
                            const res = window.KoraDB.query("SELECT * FROM mod_aprender");
                            mostrar(JSON.parse(res));
                        } catch(e) {
                            mostrar("Error al consultar: " + e.message);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://koradevs.local", demoHtml, "text/html", "UTF-8", null)
    }
}
