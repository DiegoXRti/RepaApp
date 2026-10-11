package com.repartidor.rutas.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderCoordinatePolicyTest {

    @Test
    fun editingWithSameAddressReusesExistingCoordinates() {
        val coordinates = 19.4326 to -99.1332

        val result = OrderCoordinatePolicy.initialCoordinatesForSave(
            isEditing = true,
            previousAddress = "Calle 1   Centro",
            newAddress = " Calle 1 Centro ",
            providedCoordinates = coordinates
        )

        assertEquals(coordinates, result)
    }

    @Test
    fun editingWithChangedAddressDiscardsExistingCoordinates() {
        val result = OrderCoordinatePolicy.initialCoordinatesForSave(
            isEditing = true,
            previousAddress = "Calle 1",
            newAddress = "Calle 2",
            providedCoordinates = 19.4326 to -99.1332
        )

        assertNull(result)
    }

    @Test
    fun newOrderKeepsProvidedCoordinates() {
        val coordinates = 20.6736 to -103.344

        val result = OrderCoordinatePolicy.initialCoordinatesForSave(
            isEditing = false,
            previousAddress = null,
            newAddress = "Calle nueva",
            providedCoordinates = coordinates
        )

        assertEquals(coordinates, result)
    }
}
