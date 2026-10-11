package com.repartidor.rutas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Order::class, Client::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE orders ADD COLUMN frecuenciaPropina TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE orders ADD COLUMN entregadoEn INTEGER")
                database.execSQL("UPDATE orders SET entregadoEn = creadoEn WHERE entregado = 1 AND entregadoEn IS NULL")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_orders_entregadoEn ON orders(entregadoEn)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "repartidor_rutas.db"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .build().also { INSTANCE = it }
            }
        }
    }
}
