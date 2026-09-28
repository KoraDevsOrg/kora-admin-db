package org.koradevs.admindb.db

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

data class ColumnInfo(
    val name: String,
    val type: String,
    val isPrimaryKey: Boolean,
    val isNotNull: Boolean
)

data class ForeignKeyInfo(
    val column: String,
    val parentTable: String,
    val parentColumn: String
)

data class TableSchemaInfo(
    val tableName: String,
    val scope: DataScope,
    val columns: List<ColumnInfo>,
    val foreignKeys: List<ForeignKeyInfo>,
    val rowCount: Long
)

enum class DataScope {
    LOCAL_SCOPE,   // Core tables: strictly isolated, never synced or shared (wallets, keys, logs)
    SHARED_SCOPE   // Mod tables: catalogs, shared registries, educational modules (syncable)
}

class DatabaseInspectorHelper(private val dbHelper: KoraDbOpenHelper) {

    fun getAllTables(): List<TableSchemaInfo> {
        val tables = mutableListOf<TableSchemaInfo>()
        val db = dbHelper.readableDatabase
        
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
            null
        )

        while (cursor.moveToNext()) {
            val tableName = cursor.getString(0)
            val scope = if (tableName.startsWith("core_")) DataScope.LOCAL_SCOPE else DataScope.SHARED_SCOPE
            val columns = getTableColumns(db, tableName)
            val foreignKeys = getTableForeignKeys(db, tableName)
            val rowCount = getTableRowCount(db, tableName)

            tables.add(TableSchemaInfo(tableName, scope, columns, foreignKeys, rowCount))
        }
        cursor.close()
        return tables
    }

    private fun getTableColumns(db: SQLiteDatabase, tableName: String): List<ColumnInfo> {
        val columns = mutableListOf<ColumnInfo>()
        val cursor = db.rawQuery("PRAGMA table_info(\"$tableName\")", null)
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val type = cursor.getString(cursor.getColumnIndexOrThrow("type")) ?: "TEXT"
            val pk = cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0
            val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) > 0
            columns.add(ColumnInfo(name, type, pk, notNull))
        }
        cursor.close()
        return columns
    }

    private fun getTableForeignKeys(db: SQLiteDatabase, tableName: String): List<ForeignKeyInfo> {
        val fks = mutableListOf<ForeignKeyInfo>()
        val cursor = db.rawQuery("PRAGMA foreign_key_list(\"$tableName\")", null)
        while (cursor.moveToNext()) {
            val column = cursor.getString(cursor.getColumnIndexOrThrow("from"))
            val parentTable = cursor.getString(cursor.getColumnIndexOrThrow("table"))
            val parentColumn = cursor.getString(cursor.getColumnIndexOrThrow("to"))
            fks.add(ForeignKeyInfo(column, parentTable, parentColumn))
        }
        cursor.close()
        return fks
    }

    private fun getTableRowCount(db: SQLiteDatabase, tableName: String): Long {
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM \"$tableName\"", null)
            var count = 0L
            if (cursor.moveToFirst()) {
                count = cursor.getLong(0)
            }
            cursor.close()
            count
        } catch (e: Exception) {
            0L
        }
    }

    fun purgeTableData(tableName: String): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            db.execSQL("DELETE FROM \"$tableName\"")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun dropOrphanTable(tableName: String): Boolean {
        if (tableName.startsWith("core_")) return false
        return try {
            val db = dbHelper.writableDatabase
            db.execSQL("DROP TABLE IF EXISTS \"$tableName\"")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun runVacuumAndIntegrityCheck(): String {
        val db = dbHelper.writableDatabase
        return try {
            val cursor = db.rawQuery("PRAGMA integrity_check", null)
            var result = "OK"
            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()

            if (result == "ok") {
                db.execSQL("VACUUM")
                "Integridad OK y VACUUM completado con éxito."
            } else {
                "Error de integridad: $result"
            }
        } catch (e: Exception) {
            "Error en mantenimiento: ${e.localizedMessage}"
        }
    }
}
