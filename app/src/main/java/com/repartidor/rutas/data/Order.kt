package com.repartidor.rutas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["entregado"]),
        Index(value = ["cobroEntregado"]),
        Index(value = ["entregadoEn"])
    ]
)
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String,
    val direccionTexto: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val notas: String? = null,
    val fotoUri: String? = null,
    val precio: Double = 0.0,
    val tipoPago: String = "Cobrar", // Cobrar, Transferencia, Pagado
    val propina: Double = 0.0,
    val entregado: Boolean = false,
    val cobroEntregado: Boolean = false,
    val secuencia: Int = -1,
    val creadoEn: Long = System.currentTimeMillis(),
    val entregadoEn: Long? = null,
    val frecuenciaPropina: String? = null // "Siempre", "A veces", "Nunca"
)
