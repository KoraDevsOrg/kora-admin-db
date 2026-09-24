package org.koradevs.sdk

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class KoraClient(private val context: Context) {

    companion object {
        const val AUTHORITY = "org.koradevs.provider.kora"
        val BASE_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

        // Métodos RPC expuestos por Kora Admin DB
        private const val METHOD_REGISTER_MODULE = "register_module"
        private const val METHOD_EXEC_SQL = "exec_sql"
        private const val KEY_SQL = "sql_statement"
        private const val KEY_APP_PKG = "app_package"
        private const val KEY_APP_NAME = "app_name"
        private const val KEY_APP_VER = "app_version"
        private const val KEY_RESULT = "result_status"

        const val ADMIN_PKG = "org.koradevs.admindb"
    }

    private val resolver: ContentResolver = context.contentResolver

    /**
     * Comprueba si Kora Admin DB está instalado y respondiendo en el sistema.
     */
    fun isCoreAvailable(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(ADMIN_PKG, 0)
            packageInfo != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Registra el módulo de la app satélite y ejecuta el DDL de sus tablas.
     */
    fun registerModule(appName: String, appVersion: Int, ddlSql: String): Boolean {
        val extras = Bundle().apply {
            putString(KEY_APP_PKG, context.packageName)
            putString(KEY_APP_NAME, appName)
            putInt(KEY_APP_VER, appVersion)
            putString(KEY_SQL, ddlSql)
        }

        return try {
            val response = resolver.call(BASE_CONTENT_URI, METHOD_REGISTER_MODULE, null, extras)
            response?.getBoolean(KEY_RESULT, false) ?: false
        } catch (e: SecurityException) {
            // Se dispara si la app satélite no fue firmada con la misma clave de Kora
            throw SecurityException("Firma criptográfica inválida: Esta app no pertenece a koradevsorg.")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Consulta registros de cualquier tabla mediante SQL nativo.
     */
    fun query(
        tableName: String,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Cursor? {
        val uri = Uri.withAppendedPath(BASE_CONTENT_URI, tableName)
        return resolver.query(uri, projection, selection, selectionArgs, sortOrder)
    }

    /**
     * Inserta un registro en una tabla.
     */
    fun insert(tableName: String, values: ContentValues): Uri? {
        val uri = Uri.withAppendedPath(BASE_CONTENT_URI, tableName)
        return resolver.insert(uri, values)
    }

    /**
     * Actualiza registros en una tabla.
     */
    fun update(
        tableName: String,
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val uri = Uri.withAppendedPath(BASE_CONTENT_URI, tableName)
        return resolver.update(uri, values, selection, selectionArgs)
    }

    /**
     * Elimina registros en una tabla.
     */
    fun delete(
        tableName: String,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val uri = Uri.withAppendedPath(BASE_CONTENT_URI, tableName)
        return resolver.delete(uri, selection, selectionArgs)
    }

    /**
     * Ejecuta una sentencia SQL directa (ej. DROP o migraciones aditivas).
     */
    fun executeSql(sql: String): Boolean {
        val extras = Bundle().apply { putString(KEY_SQL, sql) }
        val response = resolver.call(BASE_CONTENT_URI, METHOD_EXEC_SQL, null, extras)
        return response?.getBoolean(KEY_RESULT, false) ?: false
    }

    /**
     * Abre la tienda o guía al usuario a descargar Kora Admin DB si no está instalado.
     */
    fun promptInstallCore() {
        val intent = context.packageManager.getLaunchIntentForPackage(ADMIN_PKG)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            // Redirige al release de GitHub del Core
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/KoraDevsOrg/kora-admin-db/releases/latest")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(browserIntent)
        }
    }
}