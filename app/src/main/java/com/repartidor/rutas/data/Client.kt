package com.repartidor.rutas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["nombre"]),
        Index(value = ["telefono"])
    ]
)
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String,
    val direccion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val fotoDomicilioUri: String? = null,
    val notasGenerales: String? = null,
    val frecuenciaPropina: String? = null // "Siempre", "A veces", "Nunca"
)
