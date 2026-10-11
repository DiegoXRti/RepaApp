package com.repartidor.rutas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderDaoTipDateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val dao = db.orderDao()

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun tipsRangeUsesDeliveryDateInsteadOfCreationDate() = runBlocking {
        val yesterday = 1_000L
        val todayStart = 10_000L
        val deliveredToday = 11_000L
        val todayEnd = 12_000L
        val id = dao.insertar(
            Order(
                nombre = "Cliente",
                telefono = "5551234567",
                creadoEn = yesterday
            )
        )

        dao.marcarEntregado(id, propina = 25.0, entregadoEn = deliveredToday)

        val total = dao.obtenerPropinasRango(todayStart, todayEnd).first()

        assertEquals(25.0, total, 0.0)
    }
}
