package org.koradevs.admindb.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.koradevs.admindb.db.KoraDbOpenHelper

class KoraContentProvider : ContentProvider() {

    private lateinit var dbHelper: KoraDbOpenHelper

    companion object {
        const val AUTHORITY = "org.koradevs.provider.kora"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

        // Métodos RPC invocables por el SDK satélite
        const val METHOD_REGISTER_MODULE = "register_module"
        const val METHOD_EXEC_SQL = "exec_sql"
        const val KEY_SQL = "sql_statement"
        const val KEY_APP_PKG = "app_package"
        const val KEY_APP_NAME = "app_name"
        const val KEY_APP_VER = "app_version"
        const val KEY_RESULT = "result_status"
    }

    override fun onCreate(): Boolean {
        context?.let {
            dbHelper = KoraDbOpenHelper(it)
            return true
        }
        return false
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val db = dbHelper.writableDatabase
        val response = Bundle()

        when (method) {
            METHOD_REGISTER_MODULE -> {
                val pkgName = extras?.getString(KEY_APP_PKG) ?: return null
                val appName = extras?.getString(KEY_APP_NAME) ?: "Unknown"
                val appVer = extras?.getInt(KEY_APP_VER, 1) ?: 1
                val ddlSql = extras?.getString(KEY_SQL) ?: return null

                db.beginTransaction()
                try {
                    // 1. Ejecuta el DDL de la nueva app (ej: CREATE TABLE mod_...)
                    db.execSQL(ddlSql)

                    // 2. Registra o actualiza la app en el inventario del ecosistema
                    val values = ContentValues().apply {
                        put(KoraDbOpenHelper.COL_PKG_NAME, pkgName)
                        put(KoraDbOpenHelper.COL_APP_NAME, appName)
                        put(KoraDbOpenHelper.COL_APP_VER, appVer)
                        put(KoraDbOpenHelper.COL_REG_DATE, System.currentTimeMillis())
                        put(KoraDbOpenHelper.COL_STATUS, "ACTIVE")
                    }
                    db.insertWithOnConflict(
                        KoraDbOpenHelper.TABLE_REGISTRY,
                        null,
                        values,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )

                    db.setTransactionSuccessful()
                    response.putBoolean(KEY_RESULT, true)
                } catch (e: Exception) {
                    response.putBoolean(KEY_RESULT, false)
                    response.putString("error", e.localizedMessage)
                } finally {
                    db.endTransaction()
                }
            }

            METHOD_EXEC_SQL -> {
                val sql = extras?.getString(KEY_SQL) ?: return null
                try {
                    db.execSQL(sql)
                    response.putBoolean(KEY_RESULT, true)
                } catch (e: Exception) {
                    response.putBoolean(KEY_RESULT, false)
                    response.putString("error", e.localizedMessage)
                }
            }
        }

        return response
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val db = dbHelper.readableDatabase
        val tableName = uri.lastPathSegment ?: return null
        return db.query(tableName, projection, selection, selectionArgs, null, null, sortOrder)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val db = dbHelper.writableDatabase
        val tableName = uri.lastPathSegment ?: return null
        val id = db.insertWithOnConflict(
            tableName,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        return Uri.withAppendedPath(uri, id.toString())
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = dbHelper.writableDatabase
        val tableName = uri.lastPathSegment ?: return 0
        return db.update(tableName, values, selection, selectionArgs)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = dbHelper.writableDatabase
        val tableName = uri.lastPathSegment ?: return 0
        return db.delete(tableName, selection, selectionArgs)
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.koradevs.db"
}