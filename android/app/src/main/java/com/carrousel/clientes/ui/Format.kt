package com.carrousel.clientes.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val spanish = Locale("es", "GT")
private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", spanish)
private val dayMonthFormat = DateTimeFormatter.ofPattern("d 'de' MMMM", spanish)
private val dayTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", spanish)

/** "2026-11-04" → "4 nov 2026". */
fun prettyDate(iso: String?): String =
    iso?.let { runCatching { LocalDate.parse(it.take(10)).format(dayFormat) }.getOrDefault(it) } ?: "—"

/** "2026-11-07 18:00:00" → "sáb 7 nov, 18:00". */
fun prettyDateTime(value: String?): String =
    value?.let { runCatching { LocalDateTime.parse(it.replace(' ', 'T')).format(dayTimeFormat) }.getOrDefault(it) } ?: "—"

/** Sin tildes y en minúsculas, para buscadores. */
fun String.foldAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

/** Texto corto del próximo cumpleaños de una fecha ISO ("YYYY-MM-DD"). */
fun nextBirthdaySummary(isoBirthDate: String): String = runCatching {
    val birth = LocalDate.parse(isoBirthDate.take(10))
    val today = LocalDate.now()
    val md = MonthDay.from(birth)
    var next = md.atYear(today.year)
    if (next.isBefore(today)) next = md.atYear(today.year + 1)
    val days = ChronoUnit.DAYS.between(today, next)
    val dayText = birth.format(dayMonthFormat)
    when {
        days == 0L -> "Cumple el $dayText · ¡Hoy es su cumpleaños!"
        days == 1L -> "Cumple el $dayText · Mañana"
        days <= 30L -> "Cumple el $dayText · Faltan $days días"
        else -> "Cumple el $dayText"
    }
}.getOrDefault("Nació el ${prettyDate(isoBirthDate)}")

/** QR en blanco y negro con el código del cupón, para que el personal lo lea o lo escriba. */
fun qrBitmap(text: String, size: Int = 720): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
