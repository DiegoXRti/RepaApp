package com.repartidor.rutas.util

import com.repartidor.rutas.data.Order
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RouteOptimizer {

    /** Distancia en kilometros entre dos puntos (formula de Haversine). */
    fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radioTierra = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radioTierra * c
    }

    /**
     * Ordena los pedidos con ubicacion valida empezando desde (latActual, lonActual),
     * usando el heuristico del vecino mas cercano y luego una mejora 2-opt.
     * Devuelve la lista en el orden sugerido para recorrer, junto con la distancia
     * total estimada en kilometros. Los pedidos sin coordenadas se ignoran.
     */
    fun optimizar(
        latActual: Double,
        lonActual: Double,
        pedidos: List<Order>
    ): Pair<List<Order>, Double> {
        val conUbicacion = pedidos.filter { it.latitud != null && it.longitud != null }
        if (conUbicacion.isEmpty()) return Pair(emptyList(), 0.0)

        val restantes = conUbicacion.toMutableList()
        val ruta = mutableListOf<Order>()
        var lat = latActual
        var lon = lonActual
        val ahora = System.currentTimeMillis()

        while (restantes.isNotEmpty()) {
            val siguiente = restantes.minByOrNull {
                val distancia = distanciaKm(lat, lon, it.latitud!!, it.longitud!!)
                // Prioridad por tiempo: reducimos la distancia "virtual" 100m por cada 5 minutos de espera
                val minutosEspera = (ahora - it.creadoEn) / 60000.0
                val factorTiempo = minutosEspera * 0.02 // 0.02 km (20m) por minuto
                distancia - factorTiempo
            }!!
            ruta.add(siguiente)
            restantes.remove(siguiente)
            lat = siguiente.latitud!!
            lon = siguiente.longitud!!
        }

        val mejorada = mejora2Opt(latActual, lonActual, ruta)
        val distanciaTotal = distanciaTotalRuta(latActual, lonActual, mejorada)
        return Pair(mejorada, distanciaTotal)
    }

    private fun distanciaTotalRuta(latActual: Double, lonActual: Double, ruta: List<Order>): Double {
        var total = 0.0
        var lat = latActual
        var lon = lonActual
        for (pedido in ruta) {
            total += distanciaKm(lat, lon, pedido.latitud!!, pedido.longitud!!)
            lat = pedido.latitud!!
            lon = pedido.longitud!!
        }
        return total
    }

    /** Mejora simple 2-opt sobre la ruta obtenida por vecino mas cercano. */
    private fun mejora2Opt(latActual: Double, lonActual: Double, rutaInicial: List<Order>): List<Order> {
        if (rutaInicial.size < 4) return rutaInicial

        var ruta = rutaInicial.toMutableList()
        var mejorado = true
        var iteraciones = 0

        while (mejorado && iteraciones < 50) {
            mejorado = false
            iteraciones++
            for (i in 0 until ruta.size - 1) {
                for (j in i + 1 until ruta.size) {
                    val nuevaRuta = ruta.toMutableList()
                    var izquierda = i
                    var derecha = j
                    while (izquierda < derecha) {
                        val tmp = nuevaRuta[izquierda]
                        nuevaRuta[izquierda] = nuevaRuta[derecha]
                        nuevaRuta[derecha] = tmp
                        izquierda++
                        derecha--
                    }

                    val distActual = distanciaTotalRuta(latActual, lonActual, ruta)
                    val distNueva = distanciaTotalRuta(latActual, lonActual, nuevaRuta)
                    if (distNueva < distActual - 0.0001) {
                        ruta = nuevaRuta
                        mejorado = true
                    }
                }
            }
        }
        return ruta
    }
}
