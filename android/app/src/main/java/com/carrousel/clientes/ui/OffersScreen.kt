package com.carrousel.clientes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carrousel.clientes.data.Offer

@Composable
fun OffersScreen(offers: List<Offer>, busy: Boolean, onClaim: (Offer) -> Unit, onShowCoupon: (String) -> Unit, padding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (offers.isEmpty()) item {
            Text("No hay ofertas vigentes en este momento.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        }
        items(offers, key = { it.id }) { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(o.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(o.description)
                    Text(
                        "${o.parkName ?: "Todos los parques"} · hasta el ${prettyDate(o.endsOn)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (o.couponEnabled) {
                        o.couponLabel?.let { Text("Cupón: $it", fontWeight = FontWeight.SemiBold) }
                        o.couponTerms?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        val claimed = o.claimedCode
                        if (claimed == null) Button(onClick = { onClaim(o) }, enabled = !busy) { Text("Obtener cupón") }
                        else OutlinedButton(onClick = { onShowCoupon(claimed) }) { Text("Ver mi cupón") }
                    }
                }
            }
        }
    }
}
