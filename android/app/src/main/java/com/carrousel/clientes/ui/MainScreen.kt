package com.carrousel.clientes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.carrousel.clientes.AppViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight

enum class Tab(val label: String, val icon: ImageVector) {
    COUPONS("Cupones", Icons.Filled.Star),
    OFFERS("Ofertas", Icons.Filled.Favorite),
    PARKS("Parques", Icons.Filled.Place),
    PROFILE("Perfil", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableStateOf(Tab.COUPONS) }
    // Detalle abierto: código del cupón o id del parque. Se guardan como valores simples para sobrevivir a la rotación.
    var couponCode by rememberSaveable { mutableStateOf<String?>(null) }
    var parkId by rememberSaveable { mutableStateOf<Int?>(null) }
    val coupon = couponCode?.let { code -> vm.coupons.firstOrNull { it.code == code } }
    val park = parkId?.let { id -> vm.parks.firstOrNull { it.id == id } }
    val inDetail = coupon != null || park != null
    BackHandler(enabled = inDetail) { couponCode = null; parkId = null }

    Scaffold(
        topBar = {
            // Barra del ecosistema: franja de degradado, fondo blanco y título en azul de marca.
            androidx.compose.foundation.layout.Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
            BrandStrip()
            TopAppBar(
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.primary),
                title = { Text(coupon?.let { "Mi cupón" } ?: park?.name ?: tab.label, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    if (inDetail) IconButton(onClick = { couponCode = null; parkId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!inDetail) IconButton(onClick = { vm.refresh() }, enabled = !vm.busy) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                },
            )
            }
        },
        bottomBar = {
            if (!inDetail) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = t == tab, onClick = { tab = t }, icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                !vm.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (vm.busy) CircularProgressIndicator()
                    else androidx.compose.material3.TextButton(onClick = { vm.refresh() }) { Text("Reintentar") }
                }
                coupon != null -> CouponDetail(coupon, padding)
                park != null -> ParkDetail(park, vm.events, padding)
                else -> when (tab) {
                    Tab.COUPONS -> CouponsScreen(
                        vm.coupons, hasMembers = vm.profile?.members?.isNotEmpty() == true,
                        onOpen = { couponCode = it.code }, onAddMembers = { tab = Tab.PROFILE }, padding = padding,
                    )
                    Tab.OFFERS -> OffersScreen(
                        vm.offers, vm.busy, onClaim = { o -> vm.claim(o) { couponCode = it.code } },
                        onShowCoupon = { couponCode = it }, padding = padding,
                    )
                    Tab.PARKS -> ParksScreen(vm.parks, vm.events, onOpen = { parkId = it.id }, padding = padding)
                    Tab.PROFILE -> ProfileScreen(
                        vm.profile, vm.busy, onSave = vm::saveProfile,
                        onAddMember = { n, d, done -> vm.addMember(n, d, done) }, onLogout = vm::logout, padding = padding,
                    )
                }
            }
            if (vm.busy && vm.loaded) LinearProgressIndicator(Modifier.padding(padding).padding(horizontal = 0.dp).align(Alignment.TopCenter))
        }
    }
}
