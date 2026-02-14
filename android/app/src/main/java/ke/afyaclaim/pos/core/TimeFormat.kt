package ke.afyaclaim.pos.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dayTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US)
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)

    fun clock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        time.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun dayAndTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return if (at.toLocalDate() == LocalDate.now(zone)) "Today, ${time.format(at)}" else dayTime.format(at)
    }

    fun today(zone: ZoneId = ZoneId.systemDefault()): String = longDate.format(LocalDate.now(zone))

    fun startOfToday(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = (now - epochMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> "${minutes / (24 * 60)} d ago"
        }
    }
}
