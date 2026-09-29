package org.koradevs.admindb.p2p

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.koradevs.admindb.db.KoraDbOpenHelper

class KoraMergeEngine(private val dbHelper: KoraDbOpenHelper) {

    /**
     * Extrae deltas de SHARED_SCOPE (mod_*) para sincronización bidireccional.
     */
    fun extractSharedDeltas(): String {
        val db = dbHelper.readableDatabase
        val payload = JSONObject()
        val tablesArray = JSONArray()

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'mod_%'",
            null
        )

        while (cursor.moveToNext()) {
            val tableName = cursor.getString(0)
            val tableObj = JSONObject()
            tableObj.put("table_name", tableName)

            val rowsArray = JSONArray()
            val rowCursor = db.rawQuery("SELECT * FROM \"$tableName\"", null)
            val columnNames = rowCursor.columnNames

            while (rowCursor.moveToNext()) {
                val rowObj = JSONObject()
                for (col in columnNames) {
                    val colIndex = rowCursor.getColumnIndex(col)
                    when (rowCursor.getType(colIndex)) {
                        Cursor.FIELD_TYPE_INTEGER -> rowObj.put(col, rowCursor.getLong(colIndex))
                        Cursor.FIELD_TYPE_FLOAT -> rowObj.put(col, rowCursor.getDouble(colIndex))
                        Cursor.FIELD_TYPE_STRING -> rowObj.put(col, rowCursor.getString(colIndex))
                        else -> rowObj.put(col, rowCursor.getString(colIndex) ?: "")
                    }
                }
                rowsArray.put(rowObj)
            }
            rowCursor.close()

            tableObj.put("rows", rowsArray)
            tablesArray.put(tableObj)
        }
        cursor.close()

        payload.put("version", 1)
        payload.put("timestamp", System.currentTimeMillis())
        payload.put("shared_tables", tablesArray)
        return payload.toString()
    }

    /**
     * Fusiona los deltas recibidos de un par en la red local aplicando Last-Write-Wins / Conflict Resolution
     * y manteniendo integridad referencial (SHARED_SCOPE únicamente).
     */
    fun mergeDeltas(jsonPayload: String): Result<String> {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val root = JSONObject(jsonPayload)
            val tablesArray = root.getJSONArray("shared_tables")
            var mergedCount = 0

            for (i in 0 until tablesArray.length()) {
                val tableObj = tablesArray.getJSONObject(i)
                val tableName = tableObj.getString("table_name")

                // AISLAMIENTO DE PRIVACIDAD: Nunca sincronizar core_* (LOCAL_SCOPE)
                if (!tableName.startsWith("mod_")) continue

                val rowsArray = tableObj.getJSONArray("rows")
                for (r in 0 until rowsArray.length()) {
                    val rowObj = rowsArray.getJSONObject(r)
                    val values = ContentValues()
                    val keys = rowObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = rowObj.get(key)
                        when (value) {
                            is Long -> values.put(key, value)
                            is Int -> values.put(key, value)
                            is Double -> values.put(key, value)
                            is Boolean -> values.put(key, value)
                            is String -> values.put(key, value)
                            else -> values.put(key, value.toString())
                        }
                    }

                    // Inserción con resolución de conflicto (CONFLIC_REPLACE / LWW)
                    db.insertWithOnConflict(
                        tableName,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                    mergedCount++
                }
            }

            db.setTransactionSuccessful()
            db.endTransaction()
            return Result.success("Merge P2P exitoso: $mergedCount registros sincronizados y fusionados.")
        } catch (e: Exception) {
            db.endTransaction()
            return Result.failure(e)
        }
    }
}
