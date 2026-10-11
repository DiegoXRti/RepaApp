package com.repartidor.rutas.util

object ShareLocationParser {

    // Busca un patron "lat,lng" con al menos 3 decimales, que es como
    // WhatsApp / Google Maps escriben las coordenadas en el link compartido.
    private val patronCoordenadas = Regex("""(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")

    /**
     * Intenta extraer latitud y longitud de un texto compartido, por ejemplo:
     * "https://maps.google.com/?q=20.65900,-103.34900"
     * "Mi ubicacion actual: https://www.google.com/maps/search/?api=1&query=20.659,-103.349"
     */
    fun extraerCoordenadas(texto: String): Pair<Double, Double>? {
        val coincidencia = patronCoordenadas.find(texto) ?: return null
        val lat = coincidencia.groupValues[1].toDoubleOrNull() ?: return null
        val lon = coincidencia.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Pair(lat, lon)
    }
}
