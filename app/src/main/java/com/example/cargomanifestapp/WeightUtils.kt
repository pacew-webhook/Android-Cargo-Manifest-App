package com.example.cargomanifestapp

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Normalisasi berat untuk seluruh alur BTB.
 * Nilai internal SELALU Double dengan titik desimal.
 * Tampilan pengguna tetap mengikuti format Indonesia (koma desimal).
 */
object WeightUtils {
    private val displaySymbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID"))

    private val displayFormat = DecimalFormat("0.##", displaySymbols).apply {
        isGroupingUsed = false
    }

    fun parse(text: String): Double? {
        var s = text.trim()
            .replace("KG", "", ignoreCase = true)
            .replace("kg", "")
            .replace(" ", "")

        if (s.isBlank()) return null

        // OCR sering menghasilkan karakter mirip angka.
        s = s.replace('O', '0', ignoreCase = true)
            .replace('I', '1')
            .replace('l', '1')

        // Pertahankan hanya digit, koma, titik, dan tanda minus.
        s = s.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (s.isBlank() || s == "-") return null

        val comma = s.lastIndexOf(',')
        val dot = s.lastIndexOf('.')

        val normalized = when {
            comma >= 0 && dot >= 0 -> {
                // Separator terakhir dianggap separator desimal.
                if (comma > dot) {
                    s.replace(".", "").replace(',', '.')
                } else {
                    s.replace(",", "")
                }
            }
            comma >= 0 -> s.replace(',', '.')
            else -> s
        }

        return normalized.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 && it <= 9999.0 }
    }

    fun formatForInput(value: Double): String = displayFormat.format(value)

    fun formatForExcel(value: Double): String = String.format(Locale.US, "%.2f", value)
}
