package org.koradevs.admindb.runtime

import android.content.Context
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import java.util.Locale

class KoraTTSBridge(context: Context, private val onStatus: (String) -> Unit) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES")) // Español por defecto
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                onStatus("Idioma TTS no soportado")
            } else {
                isInitialized = true
                onStatus("Voz asistida lista")
            }
        } else {
            onStatus("Error al inicializar síntesis de voz")
        }
    }

    @JavascriptInterface
    fun speak(text: String) {
        if (isInitialized && !text.isBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KORA_TTS_ID")
        }
    }

    @JavascriptInterface
    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
