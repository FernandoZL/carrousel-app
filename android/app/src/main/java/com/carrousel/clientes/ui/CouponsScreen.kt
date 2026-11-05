package com.carrousel.clientes.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
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
        status.label, color = color, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun CouponsScreen(coupons: List<Coupon>, hasMembers: Boolean, onOpen: (Coupon) -> Unit, onAddMembers: () -> Unit, padding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (!hasMembers) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agrega a tus festejados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Registra tu fecha de nacimiento o la de tus hijos para recibir su cupón de cumpleaños.")
                    Button(onClick = onAddMembers) { Text("Agregar festejado") }
                }
            }
        }
        if (coupons.isEmpty()) item {
            Text(
                if (hasMembers) "Todavía no tienes cupones. Aparecerán aquí cuando se acerque un cumpleaños o cuando obtengas uno de una oferta."
                else "Todavía no tienes cupones.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp), textAlign = TextAlign.Center,
            )
        }
        items(coupons, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(c) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusChip(c.status)
                    }
                    Text(c.label)
                    Text("Del ${prettyDate(c.validFrom)} al ${prettyDate(c.validUntil)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Cupón a pantalla completa con el QR y el código. Sube el brillo mientras está abierto para que se lea mejor. */
@Composable
fun CouponDetail(c: Coupon, padding: PaddingValues) {
    val window = (LocalContext.current as? Activity)?.window
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
        Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        StatusChip(c.status)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
            Image(
                qr, contentDescription = "Código QR del cupón ${c.code}", filterQuality = FilterQuality.None,
                alpha = if (usable) 1f else 0.25f, modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp),
            )
        }
        Text(c.code, fontFamily = FontFamily.Monospace, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
        Text(
            when (c.status) {
                CouponStatus.AVAILABLE -> "Muéstralo en caja en cualquier parque Carrousel. Se canjea una sola vez."
                CouponStatus.UPCOMING -> "Podrás canjearlo desde el ${prettyDate(c.validFrom)}."
                CouponStatus.REDEEMED -> "Canjeado el ${prettyDate(c.redeemedAt)}${c.redeemedPark?.let { " en $it" } ?: ""}."
                CouponStatus.EXPIRED -> "Este cupón venció el ${prettyDate(c.validUntil)}."
                CouponStatus.VOID -> "Este cupón fue anulado."
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.padding(4.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Regalo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(c.label, style = MaterialTheme.typography.titleMedium)
                Text("Vigencia: ${prettyDate(c.validFrom)} al ${prettyDate(c.validUntil)}")
                if (c.terms.isNotBlank()) Text(c.terms, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
