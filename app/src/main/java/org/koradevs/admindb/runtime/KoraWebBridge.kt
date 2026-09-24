package org.koradevs.admindb.runtime

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import org.koradevs.admindb.db.KoraDbOpenHelper

class KoraWebBridge(
    private val dbHelper: KoraDbOpenHelper,
    private val onDataChanged: () -> Unit
) {

    @JavascriptInterface
    fun registerModule(pkg: String, name: String, version: Int, ddlSql: String): String {
        val db = dbHelper.writableDatabase
        return try {
            db.beginTransaction()
            db.execSQL(ddlSql)
            db.execSQL(
                "INSERT OR REPLACE INTO ${KoraDbOpenHelper.TABLE_REGISTRY} VALUES (?, ?, ?, ?, 'ACTIVE')",
                arrayOf(pkg, name, version, System.currentTimeMillis())
            )
            db.setTransactionSuccessful()
            db.endTransaction()
            onDataChanged()
            JSONObject().put("status", "SUCCESS").put("message", "Módulo y tablas registrados").toString()
        } catch (e: Exception) {
            JSONObject().put("status", "ERROR").put("message", e.localizedMessage).toString()
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun query(sql: String, argsJson: String = "[]"): String {
        val db = dbHelper.readableDatabase
        val result = JSONArray()
        return try {
            val selectionArgs = if (argsJson.isNotEmpty() && argsJson != "[]") {
                val argsArray = JSONArray(argsJson)
                Array(argsArray.length()) { i -> argsArray.getString(i) }
            } else {
                null
            }

            val cursor = db.rawQuery(sql, selectionArgs)
            val columnNames = cursor.columnNames

            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (col in columnNames) {
                    val index = cursor.getColumnIndex(col)
                    when (cursor.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(col, cursor.getLong(index))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(col, cursor.getDouble(index))
                        android.database.Cursor.FIELD_TYPE_STRING -> row.put(col, cursor.getString(index))
                        android.database.Cursor.FIELD_TYPE_BLOB -> row.put(col, "[BLOB]")
                        else -> row.put(col, JSONObject.NULL)
                    }
                }
                result.put(row)
            }
            cursor.close()
            result.toString()
        } catch (e: Exception) {
            JSONObject().put("status", "ERROR").put("message", e.localizedMessage).toString()
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun execute(sql: String, argsJson: String = "[]"): String {
        val db = dbHelper.writableDatabase
        return try {
            if (argsJson.isNotEmpty() && argsJson != "[]") {
                val argsArray = JSONArray(argsJson)
                val bindArgs = Array<Any>(argsArray.length()) { i -> argsArray.get(i) }
                db.execSQL(sql, bindArgs)
            } else {
                db.execSQL(sql)
            }
            onDataChanged()
            JSONObject().put("status", "SUCCESS").toString()
        } catch (e: Exception) {
            JSONObject().put("status", "ERROR").put("message", e.localizedMessage).toString()
        }
    }
}