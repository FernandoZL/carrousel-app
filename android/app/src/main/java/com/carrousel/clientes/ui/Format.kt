package com.carrousel.clientes.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale("es", "GT")
private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", spanish)
private val dayTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", spanish)

/** "2026-11-04" → "4 nov 2026". */
fun prettyDate(iso: String?): String =
    iso?.let { runCatching { LocalDate.parse(it.take(10)).format(dayFormat) }.getOrDefault(it) } ?: "—"

/** "2026-11-07 18:00:00" → "sáb 7 nov, 18:00". */
fun prettyDateTime(value: String?): String =
    value?.let { runCatching { LocalDateTime.parse(it.replace(' ', 'T')).format(dayTimeFormat) }.getOrDefault(it) } ?: "—"

/** QR en blanco y negro con el código del cupón, para que el personal lo lea o lo escriba. */
fun qrBitmap(text: String, size: Int = 720): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
