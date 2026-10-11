package com.repartidor.rutas.util

object OrderCoordinatePolicy {

    fun initialCoordinatesForSave(
        isEditing: Boolean,
        previousAddress: String?,
        newAddress: String?,
        providedCoordinates: Pair<Double, Double>?
    ): Pair<Double, Double>? {
        if (!isEditing) return providedCoordinates
        return if (normalizeAddress(previousAddress) == normalizeAddress(newAddress)) {
            providedCoordinates
        } else {
            null
        }
    }

    private fun normalizeAddress(address: String?): String? {
        return address
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotEmpty() }
    }
}
