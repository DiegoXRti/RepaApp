package com.repartidor.rutas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration7To9AddsNewColumnsWithoutDeletingOrders() {
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    nombre TEXT NOT NULL,
                    telefono TEXT NOT NULL,
                    direccionTexto TEXT,
                    latitud REAL,
                    longitud REAL,
                    notas TEXT,
                    fotoUri TEXT,
                    precio REAL NOT NULL,
                    tipoPago TEXT NOT NULL,
                    propina REAL NOT NULL,
                    entregado INTEGER NOT NULL,
                    cobroEntregado INTEGER NOT NULL,
                    secuencia INTEGER NOT NULL,
                    creadoEn INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_entregado ON orders(entregado)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_cobroEntregado ON orders(cobroEntregado)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS clients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    nombre TEXT NOT NULL,
                    telefono TEXT NOT NULL,
                    direccion TEXT,
                    latitud REAL,
                    longitud REAL,
                    fotoDomicilioUri TEXT,
                    notasGenerales TEXT,
                    frecuenciaPropina TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_nombre ON clients(nombre)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_telefono ON clients(telefono)")
            db.execSQL(
                """
                INSERT INTO orders (
                    nombre, telefono, direccionTexto, latitud, longitud, notas, fotoUri,
                    precio, tipoPago, propina, entregado, cobroEntregado, secuencia, creadoEn
                ) VALUES (
                    'Cliente existente', '5551234567', 'Calle 1', 19.43, -99.13, NULL, NULL,
                    120.0, 'Cobrar', 15.0, 1, 0, 1, 1000
                )
                """.trimIndent()
            )
            db.version = 7
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
            .build()

        migrated.openHelper.writableDatabase.query(
            "SELECT nombre, frecuenciaPropina, entregadoEn FROM orders WHERE id = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Cliente existente", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertEquals(1000L, cursor.getLong(2))
        }
        migrated.close()
    }
}
