package com.repartidor.rutas.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object TicketScanner {

    fun scanTicket(
        context: Context,
        imageUri: Uri,
        onSuccess: (name: String?, phone: String?, price: Double?, address: String?, notes: String?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromFilePath(context, imageUri)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text }

                var detectedPhone: String? = null
                var detectedPrice: Double? = null
                var detectedName: String? = null
                var detectedAddress: String? = null
                val detectedItems = mutableListOf<String>()

                // 1. Buscar Teléfono (Patrón de 10 dígitos)
                val phoneRegex = Regex("""(\d{10})|(\d{3}-\d{3}-\d{4})|(\(\d{3}\)\s\d{3}-\d{4})""")
                detectedPhone = phoneRegex.find(fullText)?.value?.replace(Regex("[^0-9]"), "")

                // 2. Buscar Precio (Total)
                // Busca el número más alto que parezca un total, o el que sigue a la palabra "Total"
                val priceRegex = Regex("""(?i)(total|pago|monto|neto).{0,5}(\d+(\.\d{1,2})?)""")
                val priceMatch = priceRegex.find(fullText)
                detectedPrice = priceMatch?.groupValues?.get(2)?.toDoubleOrNull()

                if (detectedPrice == null) {
                    val allNumbers = Regex("""\d+\.\d{2}""").findAll(fullText)
                        .mapNotNull { it.value.toDoubleOrNull() }
                        .toList()
                    if (allNumbers.isNotEmpty()) {
                        detectedPrice = allNumbers.maxOrNull()
                    }
                }

                // 3. Buscar Dirección y Nombre con filtros más finos
                lines.forEach { line ->
                    val text = line.trim()
                    
                    // Detectar Dirección (Palabras clave comunes en México/Latam)
                    if (detectedAddress == null && 
                        Regex("(?i)(Calle|Colonia|Col\\.|Av\\.|Avenida|Fracc|C\\.|#|CP|C\\.P\\.)").containsMatchIn(text)) {
                        detectedAddress = text
                    }
                    
                    // Detectar Nombre (Heurística: Línea con "Cliente" o "Para")
                    if (detectedName == null && 
                        (text.contains("Cliente", ignoreCase = true) || text.contains("Nombre", ignoreCase = true) || text.contains("Para:", ignoreCase = true))) {
                        detectedName = text.replace(Regex("(?i)(Cliente|Nombre|Para):"), "").trim()
                    }

                    // Artículos (Líneas que empiezan con números de cantidad o parecen productos)
                    if (Regex("""^\d+\s+[a-zA-Z]""").containsMatchIn(text)) {
                        detectedItems.add(text)
                    }
                }

                // Si no se detectó nombre arriba, intentar con la primera línea que no sea el nombre del negocio (típicamente arriba de todo)
                if (detectedName == null && lines.size > 1) {
                    detectedName = lines[1].trim() // Usualmente el nombre del cliente está debajo del encabezado
                }

                onSuccess(detectedName, detectedPhone, detectedPrice, detectedAddress, detectedItems.joinToString("\n").ifEmpty { fullText.take(200) })
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}