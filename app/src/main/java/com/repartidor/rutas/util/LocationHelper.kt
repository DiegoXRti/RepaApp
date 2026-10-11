package com.repartidor.rutas.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    /**
     * Devuelve (latitud, longitud) del ultimo dato de GPS conocido, o null
     * si no se pudo obtener. Requiere que el permiso de ubicacion ya haya
     * sido concedido antes de llamar a esta funcion.
     */
    @SuppressLint("MissingPermission")
    suspend fun obtenerUbicacionActual(context: Context): Pair<Double, Double>? {
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { continuacion ->
            cliente.lastLocation
                .addOnSuccessListener { ubicacion ->
                    if (ubicacion != null) {
                        continuacion.resume(Pair(ubicacion.latitude, ubicacion.longitude))
                    } else {
                        continuacion.resume(null)
                    }
                }
                .addOnFailureListener {
                    continuacion.resume(null)
                }
        }
    }
}
