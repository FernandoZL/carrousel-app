package com.carrousel.clientes.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.carrousel.clientes.R
import com.carrousel.clientes.data.Event
import com.carrousel.clientes.data.Park
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private enum class ParkFilter { ALL, PARKS, KIDDIES }

/** Distancia en kilómetros entre el usuario y un parque (o null si no hay coordenadas). */
private fun distanceKm(userLat: Double?, userLon: Double?, park: Park): Double? {
    val pLat = park.effectiveLat ?: return null
    val pLon = park.effectiveLon ?: return null
    if (userLat == null || userLon == null) return null
    val res = FloatArray(1)
    Location.distanceBetween(userLat, userLon, pLat, pLon, res)
    return (res[0] / 1000.0)
}

private fun formatDistance(km: Double): String =
    if (km < 1.0) "A ${(km * 1000).roundToInt()} m" else String.format(java.util.Locale.US, "A %.1f km", km)

/** Obtiene la ubicación actual o más reciente del teléfono cuando el permiso está concedido. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun fetchDeviceLocation(context: Context, onLocation: (Double, Double) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    val best = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (best != null) onLocation(best.latitude, best.longitude)
    val activeProvider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return
    runCatching {
        lm.requestSingleUpdate(activeProvider, object : LocationListener {
            override fun onLocationChanged(loc: Location) { onLocation(loc.latitude, loc.longitude) }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }, Looper.getMainLooper())
    }
}

/** Pin de ubicación con el logo oficial de Carrousel. */
@Composable
private fun CarrouselLogoPin(isKiddies: Boolean, modifier: Modifier = Modifier) {
    val border = if (isKiddies) Orange else Brand
    Box(
        modifier = modifier
            .size(48.dp)
            .background(border, CircleShape)
            .padding(2.dp)
            .background(Color.White, CircleShape)
            .clip(CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_carrousel),
            contentDescription = "Carrousel",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EventCard(e: Event) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                val timeRange = buildString {
                    append(prettyDateTime(e.startsAt))
                    e.endsAt?.takeIf { it.isNotBlank() }?.let { end ->
                        val endHour = end.substringAfter(' ', "").take(5)
                        if (end.take(10) == e.startsAt.take(10) && endHour.isNotEmpty()) append(" – $endHour")
                        else append(" al ${prettyDateTime(end)}")
                    }
                }
                Text(timeRange, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(e.description, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(e.parkName ?: "Todos los parques", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ParksScreen(parks: List<Park>, events: List<Event>, onOpen: (Park) -> Unit, padding: PaddingValues) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ParkFilter.ALL) }
    var showMap by rememberSaveable { mutableStateOf(true) }
    var userLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var userLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) fetchDeviceLocation(context) { lat, lon -> userLat = lat; userLon = lon }
    }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) fetchDeviceLocation(context) { lat, lon -> userLat = lat; userLon = lon }
    }

    val parksCount = remember(parks) { parks.count { !it.isKiddies } }
    val kiddiesCount = remember(parks) { parks.count { it.isKiddies } }
    val filtered = remember(parks, query, filter, userLat, userLon) {
        val q = query.trim().foldAccents()
        val list = parks.filter { p ->
            val matchesFilter = when (filter) {
                ParkFilter.ALL -> true
                ParkFilter.PARKS -> !p.isKiddies
                ParkFilter.KIDDIES -> p.isKiddies
            }
            val matchesQuery = q.isEmpty() || p.name.foldAccents().contains(q) || p.address.foldAccents().contains(q)
            matchesFilter && matchesQuery
        }
        if (userLat != null && userLon != null) {
            list.sortedBy { distanceKm(userLat, userLon, it) ?: Double.MAX_VALUE }
        } else list
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (events.isNotEmpty() && query.isBlank()) {
            item { Text("Próximos eventos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(events, key = { "e" + it.id }) { EventCard(it) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ubicaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (hasLocationPermission) fetchDeviceLocation(context) { lat, lon -> userLat = lat; userLon = lon }
                                else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                            Text(if (userLat != null) "Cerca de mí" else "Mi ubicación", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { showMap = !showMap }) {
                            Icon(
                                if (showMap) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.Map,
                                contentDescription = if (showMap) "Ocultar mapa" else "Ver mapa",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (showMap && filtered.isNotEmpty()) {
                    ParksMapCard(
                        parks = filtered,
                        userLat = userLat,
                        userLon = userLon,
                        onSelectPark = { id -> parks.firstOrNull { it.id == id }?.let(onOpen) },
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar por nombre, zona o departamento…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kiddiesCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == ParkFilter.ALL,
                            onClick = { filter = ParkFilter.ALL },
                            label = { Text("Todos (${parks.size})") },
                        )
                        FilterChip(
                            selected = filter == ParkFilter.PARKS,
                            onClick = { filter = ParkFilter.PARKS },
                            label = { Text("Parques ($parksCount)") },
                        )
                        FilterChip(
                            selected = filter == ParkFilter.KIDDIES,
                            onClick = { filter = ParkFilter.KIDDIES },
                            label = { Text("Kiddies ($kiddiesCount)") },
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) item {
            Text(
                if (parks.isEmpty()) "Pronto publicaremos la lista de parques."
                else "No encontramos ubicaciones con \"$query\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            )
        }
        items(filtered, key = { "p" + it.id }) { p ->
            val dist = distanceKm(userLat, userLon, p)
            Card(
                Modifier.fillMaxWidth().clickable { onOpen(p) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CarrouselLogoPin(isKiddies = p.isKiddies)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                            if (p.isKiddies) {
                                Text(
                                    "KIDDIES",
                                    color = Orange,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.background(Orange.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            if (dist != null) {
                                Text(
                                    formatDistance(dist),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (p.address.isNotBlank()) Text(p.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.hours.isNotBlank()) Text(p.hours, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Mapa interactivo con marcadores del logo oficial de Carrousel y geolocalización del usuario. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ParksMapCard(
    parks: List<Park>,
    userLat: Double?,
    userLon: Double?,
    onSelectPark: (Int) -> Unit,
) {
    val context = LocalContext.current
    val logoDataUri = remember {
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.logo_carrousel)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 96, 64, true)
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        }.getOrDefault("")
    }
    val markersJson = remember(parks) {
        val arr = JSONArray()
        parks.forEach { p ->
            val lat = p.effectiveLat ?: return@forEach
            val lon = p.effectiveLon ?: return@forEach
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("address", p.address)
                    .put("kiddies", p.isKiddies)
                    .put("lat", lat)
                    .put("lon", lon)
            )
        }
        arr.toString()
    }
    Card(
        Modifier.fillMaxWidth().height(250.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun openPark(id: Int) {
                            post { onSelectPark(id) }
                        }
                    }, "CarrouselBridge")
                }
            },
            update = { webView ->
                val uLat = userLat?.toString() ?: "null"
                val uLon = userLon?.toString() ?: "null"
                val html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
                      <style>
                        html,body,#map{margin:0;padding:0;width:100%;height:100%;font-family:system-ui,sans-serif;background:#e4eaf5}
                        .c-pin{width:40px;height:40px;border-radius:50%;background:#fff;border:3px solid #213a8f;box-shadow:0 3px 8px rgba(16,24,40,.32);display:flex;align-items:center;justify-content:center;overflow:hidden;position:relative}
                        .c-pin.kiddies{border-color:#f28b24}
                        .c-pin img{width:32px;height:32px;object-fit:contain}
                        .u-pin{width:16px;height:16px;border-radius:50%;background:#213a8f;border:3px solid #fff;box-shadow:0 0 0 4px rgba(33,58,143,.28)}
                        .pop-btn{margin-top:6px;padding:5px 10px;border:0;border-radius:6px;background:#213a8f;color:#fff;font-weight:700;font-size:12px;cursor:pointer;width:100%}
                      </style>
                    </head>
                    <body>
                      <div id="map"></div>
                      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                      <script>
                        const parks = $markersJson;
                        const uLat = $uLat, uLon = $uLon;
                        const map = L.map('map', { zoomControl: true, attributionControl: false }).setView([14.62, -90.53], 8);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18 }).addTo(map);
                        const bounds = [];
                        parks.forEach(p => {
                          const icon = L.divIcon({
                            className: '',
                            html: '<div class="c-pin ' + (p.kiddies ? 'kiddies' : '') + '"><img src="$logoDataUri" alt="Carrousel"></div>',
                            iconSize: [40, 40],
                            iconAnchor: [20, 20],
                            popupAnchor: [0, -18]
                          });
                          const m = L.marker([p.lat, p.lon], { icon }).addTo(map);
                          const div = document.createElement('div');
                          const b = document.createElement('strong');
                          b.textContent = p.name;
                          div.appendChild(b);
                          const btn = document.createElement('button');
                          btn.className = 'pop-btn';
                          btn.textContent = 'Ver parque';
                          btn.onclick = () => window.CarrouselBridge.openPark(p.id);
                          div.appendChild(btn);
                          m.bindPopup(div);
                          bounds.push([p.lat, p.lon]);
                        });
                        if (uLat !== null && uLon !== null) {
                          const uIcon = L.divIcon({ className: '', html: '<div class="u-pin"></div>', iconSize: [16, 16], iconAnchor: [8, 8] });
                          L.marker([uLat, uLon], { icon: uIcon }).addTo(map).bindPopup('Tu ubicación');
                          map.setView([uLat, uLon], 12);
                        } else if (bounds.length === 1) {
                          map.setView(bounds[0], 14);
                        } else if (bounds.length > 1) {
                          map.fitBounds(bounds, { padding: [24, 24], maxZoom: 12 });
                        }
                      </script>
                    </body>
                    </html>
                """.trimIndent()
                if (webView.tag != html) {
                    webView.tag = html
                    webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
                }
            },
        )
    }
}

@Composable
fun ParkDetail(p: Park, events: List<Event>, padding: PaddingValues) {
    val context = LocalContext.current
    fun open(uri: Uri, action: String = Intent.ACTION_VIEW) {
        try { context.startActivity(Intent(action, uri)) } catch (_: ActivityNotFoundException) { }
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                BrandStrip()
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val accent = if (p.isKiddies) Orange else Brand
                        CarrouselLogoPin(isKiddies = p.isKiddies)
                        Column {
                            Text(if (p.isKiddies) "Kiddies Carrousel" else "Parque Carrousel", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                            Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (p.address.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(p.address, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (p.hours.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Horario: ${p.hours}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val query = Uri.encode(listOf("Carrousel ${p.name}", p.address).filter { it.isNotBlank() }.joinToString(", "))
                            val lat = p.effectiveLat
                            val lon = p.effectiveLon
                            val uri = if (lat != null && lon != null) "geo:$lat,$lon?q=$lat,$lon($query)" else "geo:0,0?q=$query"
                            open(Uri.parse(uri))
                        }) {
                            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                            Text("Cómo llegar")
                        }
                        if (p.phone.isNotBlank()) {
                            OutlinedButton(onClick = { open(Uri.parse("tel:" + p.phone.filter { it.isDigit() || it == '+' }), Intent.ACTION_DIAL) }) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                                Text("Llamar")
                            }
                        }
                    }
                }
            }
        }
        ParksMapCard(
            parks = listOf(p),
            userLat = null,
            userLon = null,
            onSelectPark = {},
        )
        val here = events.filter { it.parkId == null || it.parkId == p.id }
        if (here.isNotEmpty()) {
            Text("Eventos en este parque", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            here.forEach { EventCard(it) }
        }
    }
}
