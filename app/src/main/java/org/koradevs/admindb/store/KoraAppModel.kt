package org.koradevs.admindb.store

enum class KoraAppType {
    WEB_APP,  // Corre dentro de KoraWebViewActivity
    NATIVE_APK // Se descarga e instala en Android
}

data class KoraAppItem(
    val id: String,
    val name: String,
    val description: String,
    val type: KoraAppType,
    val packageName: String,
    val version: String,
    val versionCode: Int,
    val sourceUrl: String // URL del APK en GitHub Releases o entrypoint HTML
)