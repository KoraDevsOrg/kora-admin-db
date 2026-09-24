package org.koradevs.admindb.runtime

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import org.koradevs.admindb.db.KoraDbOpenHelper

class KoraWebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var dbHelper: KoraDbOpenHelper

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = KoraDbOpenHelper(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            addJavascriptInterface(KoraWebBridge(dbHelper) {}, "KoraDB")

            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    android.util.Log.d("KORA_JS_LOG", "${message?.message()} -- From line ${message?.lineNumber()}")
                    return true
                }
            }
        }

        setContentView(webView)

        val demoHtml = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Kora Web Demo</title>
                <style>
                    body { font-family: sans-serif; padding: 16px; background: #121212; color: #fff; }
                    button { background: #2E7D32; color: white; border: none; padding: 14px; border-radius: 8px; width: 100%; margin: 6px 0; font-size: 15px; font-weight: bold; cursor: pointer; }
                    button:active { background: #1B5E20; }
                    pre { background: #1e1e1e; padding: 12px; border-radius: 6px; overflow-x: auto; color: #81C784; font-size: 13px; max-height: 250px; }
                    .error { color: #E57373 !important; }
                </style>
            </head>
            <body>
                <h3>Micro-App: Kora Japonés</h3>
                <button onclick="crearModulo()">1. Registrar Módulo y Tablas</button>
                <button onclick="insertarPalabra()">2. Insertar Palabra</button>
                <button onclick="consultar()">3. Consultar Registros</button>

                <p style="margin-top:16px; color:#aaa; font-size:12px;">Resultado de la base de datos:</p>
                <pre id="output">Esperando acción...</pre>

                <script>
                    function mostrar(data, esError = false) {
                        const out = document.getElementById('output');
                        out.className = esError ? 'error' : '';
                        out.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
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