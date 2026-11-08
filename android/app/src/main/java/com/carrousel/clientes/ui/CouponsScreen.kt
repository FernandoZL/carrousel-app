package com.carrousel.clientes.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrousel.clientes.data.Coupon
import com.carrousel.clientes.data.CouponStatus

@Composable
fun StatusChip(status: CouponStatus) {
    val color = when (status) {
        CouponStatus.AVAILABLE -> successColor()
        CouponStatus.UPCOMING -> warningColor()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        status.label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun CouponsScreen(
    coupons: List<Coupon>,
    hasMembers: Boolean,
    onOpen: (Coupon) -> Unit,
    onAddMembers: () -> Unit,
    onGoOffers: () -> Unit = {},
    padding: PaddingValues,
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val active = remember(coupons) { coupons.filter { it.status == CouponStatus.AVAILABLE || it.status == CouponStatus.UPCOMING } }
    val history = remember(coupons) { coupons.filter { it.status != CouponStatus.AVAILABLE && it.status != CouponStatus.UPCOMING } }
    val visible = if (showHistory) history else active

    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (!hasMembers) item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                        }
                        Text("Agrega a tus festejados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Registra tu fecha de nacimiento o la de tus hijos para recibir automáticamente su cupón de cumpleaños cada año.")
                    Button(onClick = onAddMembers) { Text("Agregar festejado") }
                }
            }
        }
        if (coupons.isNotEmpty() && history.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showHistory,
                    onClick = { showHistory = false },
                    label = { Text("Activos (${active.size})") },
                )
                FilterChip(
                    selected = showHistory,
                    onClick = { showHistory = true },
                    label = { Text("Historial (${history.size})") },
                )
            }
        }
        if (visible.isEmpty()) item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    }
                    Text(
                        if (showHistory) "Sin cupones en el historial" else "Todavía no tienes cupones activos",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                    Text(
                        if (showHistory) "Aquí aparecerán tus cupones ya canjeados o vencidos."
                        else if (hasMembers) "Tus cupones de cumpleaños se activan cuando se acerca la fecha, o puedes obtener uno ahora en la pestaña de Ofertas."
                        else "Agrega a tus festejados o revisa las ofertas vigentes para obtener tu primer cupón.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    if (!showHistory) {
                        OutlinedButton(onClick = onGoOffers) { Text("Ver ofertas disponibles") }
                    }
                }
            }
        }
        items(visible, key = { it.id }) { c ->
            CouponCard(c, onClick = { onOpen(c) })
        }
    }
}

@Composable
private fun CouponCard(c: Coupon, onClick: () -> Unit) {
    val accent = if (c.isBirthday) Violet else Brand
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(if (c.isBirthday) BrandGradient else Brush.horizontalGradient(listOf(Brand, Brand2))))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(
                            if (c.isBirthday) Icons.Filled.Cake else Icons.Filled.LocalOffer,
                            contentDescription = null, tint = accent,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (c.isBirthday) "CUMPLEAÑOS" else "OFERTA ESPECIAL",
                            style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.ExtraBold,
                        )
                        Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    StatusChip(c.status)
                }
                Text(c.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Válido: ${prettyDate(c.validFrom)} – ${prettyDate(c.validUntil)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            c.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Cupón a pantalla completa con el QR y el código. Sube el brillo mientras está abierto para que se lea mejor. */
@Composable
fun CouponDetail(c: Coupon, onCopied: () -> Unit = {}, padding: PaddingValues) {
    val window = (LocalContext.current as? Activity)?.window
    val clipboard = LocalClipboardManager.current
    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose { }
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = previous } }
    }
    val qr = remember(c.code) { qrBitmap(c.code) }
    val usable = c.status == CouponStatus.AVAILABLE || c.status == CouponStatus.UPCOMING
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandStrip()
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusChip(c.status)
                    Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE4E7EC)),
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                    ) {
                        Image(
                            qr, contentDescription = "Código QR del cupón ${c.code}", filterQuality = FilterQuality.None,
                            alpha = if (usable) 1f else 0.25f, modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(14.dp),
                        )
                    }
                    Text(c.code, fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(c.code))
                            onCopied()
                        },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                        Text("Copiar código")
                    }
                    Text(
                        when (c.status) {
                            CouponStatus.AVAILABLE -> "Muéstralo en caja en cualquier parque Carrousel. Se canjea una sola vez."
                            CouponStatus.UPCOMING -> "Podrás canjearlo desde el ${prettyDate(c.validFrom)}."
                            CouponStatus.REDEEMED -> "Canjeado el ${prettyDate(c.redeemedAt)}${c.redeemedPark?.let { " en $it" } ?: ""}."
                            CouponStatus.EXPIRED -> "Este cupón venció el ${prettyDate(c.validUntil)}."
                            CouponStatus.VOID -> "Este cupón fue anulado."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.padding(2.dp))
        Card(
            Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Beneficio del cupón", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(c.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Vigencia: ${prettyDate(c.validFrom)} al ${prettyDate(c.validUntil)}", style = MaterialTheme.typography.bodyMedium)
                if (c.terms.isNotBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Condiciones", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(c.terms, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
