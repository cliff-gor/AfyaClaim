package ke.afyaclaim.pos.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formats KES amounts stored as integer cents, e.g. 125000 -> "KES 1,250". */
object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)

    fun format(cents: Long, withCurrency: Boolean = true): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val whole = DecimalFormat("#,##0", symbols).format(abs / 100)
        val fraction = abs % 100
        val number = if (fraction == 0L) whole else "$whole.${fraction.toString().padStart(2, '0')}"
        val signed = if (negative) "-$number" else number
        return if (withCurrency) "KES $signed" else signed
    }
}
