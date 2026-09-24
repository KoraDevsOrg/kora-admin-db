package org.koradevs.admindb.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KoraDbOpenHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "kora_master.db"
        const val DATABASE_VERSION = 1

        // Tabla 1: Registro de aplicaciones conectadas
        const val TABLE_REGISTRY = "core_apps_registry"
        const val COL_PKG_NAME = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_APP_VER = "app_version"
        const val COL_REG_DATE = "registered_at"
        const val COL_STATUS = "status"

        // Tabla 2: El 'Instinto' / Ancla de secuencia anti-rollback
        const val TABLE_ANCHOR = "core_state_anchor"
        const val COL_USER_ID = "user_id"
        const val COL_SEQ_NUM = "latest_seq_num"
        const val COL_BALANCE = "balance"
        const val COL_STATE_HASH = "state_hash"
        const val COL_SIGNATURE = "last_signature"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Registro de apps del ecosistema
        val createRegistrySql = """
            CREATE TABLE IF NOT EXISTS $TABLE_REGISTRY (
                $COL_PKG_NAME TEXT PRIMARY KEY,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_APP_VER INTEGER NOT NULL,
                $COL_REG_DATE INTEGER NOT NULL,
                $COL_STATUS TEXT DEFAULT 'ACTIVE'
            );
        """.trimIndent()

        // 2. Ancla monótona inmutable para evitar reversión de monedas
        val createAnchorSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_ANCHOR (
                $COL_USER_ID TEXT PRIMARY KEY,
                $COL_SEQ_NUM INTEGER NOT NULL DEFAULT 0,
                $COL_BALANCE INTEGER NOT NULL DEFAULT 0,
                $COL_STATE_HASH TEXT NOT NULL,
                $COL_SIGNATURE TEXT NOT NULL
            );
        """.trimIndent()

        db.execSQL(createRegistrySql)
        db.execSQL(createAnchorSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // En una base de datos Local-First relacional, nunca borramos tablas existentes
        // Las migraciones de módulos futuros se gestionan aditivamente
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Habilitar claves foráneas y modo WAL para máxima velocidad en lecturas/escrituras concurrentes
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }
}