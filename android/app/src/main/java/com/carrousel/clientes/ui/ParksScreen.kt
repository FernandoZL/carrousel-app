package com.carrousel.clientes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carrousel.clientes.data.Event
import com.carrousel.clientes.data.Park

@Composable
private fun EventCard(e: Event) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(prettyDateTime(e.startsAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(e.description)
            Text(e.parkName ?: "Todos los parques", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ParksScreen(parks: List<Park>, events: List<Event>, onOpen: (Park) -> Unit, padding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (events.isNotEmpty()) {
            item { Text("Próximos eventos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(events, key = { "e" + it.id }) { EventCard(it) }
        }
        item { Text("Parques", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        if (parks.isEmpty()) item { Text("Pronto publicaremos la lista de parques.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(parks, key = { "p" + it.id }) { p ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(p) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (p.address.isNotBlank()) Text(p.address)
                    if (p.hours.isNotBlank()) Text(p.hours, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ParkDetail(p: Park, events: List<Event>, padding: PaddingValues) {
    val context = LocalContext.current
    fun open(uri: Uri, action: String = Intent.ACTION_VIEW) {
        try { context.startActivity(Intent(action, uri)) } catch (_: ActivityNotFoundException) { }
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (p.address.isNotBlank()) Text(p.address)
        if (p.hours.isNotBlank()) Text("Horario: ${p.hours}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val query = Uri.encode(listOf("Carrousel ${p.name}", p.address).filter { it.isNotBlank() }.joinToString(", "))
                val uri = if (p.latitude != null && p.longitude != null) "geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}($query)" else "geo:0,0?q=$query"
                open(Uri.parse(uri))
            }) { Text("Cómo llegar") }
            if (p.phone.isNotBlank()) OutlinedButton(onClick = { open(Uri.parse("tel:" + p.phone.filter { it.isDigit() || it == '+' }), Intent.ACTION_DIAL) }) { Text("Llamar") }
        }
        val here = events.filter { it.parkId == null || it.parkId == p.id }
        if (here.isNotEmpty()) {
            Text("Eventos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            here.forEach { EventCard(it) }
        }
    }
}
