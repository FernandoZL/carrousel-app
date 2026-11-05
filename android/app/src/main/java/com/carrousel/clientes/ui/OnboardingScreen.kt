package com.carrousel.clientes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrousel.clientes.R
import kotlinx.coroutines.launch

// Verde de la estrella del logo.
private val LogoGreen = Color(0xFF9BC31C)

/** Fondo claro con degradado suave, igual en presentación y bienvenida. */
private val SoftBackground = Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F4FA), Color(0xFFE4EAF5)))

private data class Slide(val kicker: String?, val pill: String, val text: String, val icon: ImageVector)

private val slides = listOf(
    Slide("Bienvenido a tu nueva", "App de diversión", "Tus cupones, ofertas y parques Carrousel en un solo lugar.", Icons.Filled.Attractions),
    Slide(null, "Celebra tu cumpleaños", "Registra a tus festejados y recibe su cupón de regalo con código QR para canjear en caja.", Icons.Filled.Cake),
    Slide(null, "Ofertas y eventos", "Sé de los primeros en enterarte de promociones, eventos y novedades de cada parque.", Icons.Filled.Celebration),
)

/** Presentación de la primera vez: tres pantallas deslizables con "Omitir" y "Comenzar". */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Box(Modifier.fillMaxSize().background(SoftBackground).systemBarsPadding()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page -> SlidePage(slides[page]) }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrimaryButton(if (last) "Comenzar" else "Siguiente") {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(slides.size) { i ->
                        val active = i == pager.currentPage
                        Box(Modifier.height(8.dp).width(if (active) 22.dp else 8.dp)
                            .background(if (active) Brand else Brand.copy(alpha = 0.25f), CircleShape))
                    }
                }
                if (!last) TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) { Text("Omitir", color = Brand) }
            }
        }
    }
}

@Composable
private fun SlidePage(s: Slide) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 150.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo_carrousel), contentDescription = "Carrousel", modifier = Modifier.width(190.dp).height(128.dp))
        Spacer(Modifier.height(18.dp))
        Illustration(s.icon)
        Spacer(Modifier.height(26.dp))
        if (s.kicker != null) {
            Text(s.kicker.uppercase(), color = Brand, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Pill(s.pill)
        Spacer(Modifier.height(14.dp))
        Text(s.text, color = Brand, fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp))
    }
}

/** Título en cápsula azul con letras amarillas, como los avisos del ecosistema. */
@Composable
private fun Pill(text: String) {
    Box(Modifier.background(Brand, RoundedCornerShape(50)).padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(text.uppercase(), color = Yellow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 21.sp, textAlign = TextAlign.Center)
    }
}

/** Ilustración propia: círculo de marca con el ícono y estrellas de los colores del logo alrededor. */
@Composable
private fun Illustration(icon: ImageVector) {
    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(250.dp)) {
            drawCircle(Brush.linearGradient(listOf(Color(0xFFE3E9F6), Color(0xFFF4F6FB))))
            drawArc(BrandGradient, startAngle = 200f, sweepAngle = 140f, useCenter = false, style = Stroke(width = 10f, cap = StrokeCap.Round))
        }
        Box(Modifier.size(150.dp).background(Brand, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Yellow, modifier = Modifier.size(86.dp))
        }
        StarAt(LogoGreen, 40.dp, -100.dp, -86.dp)
        StarAt(Pink, 34.dp, 104.dp, -70.dp)
        StarAt(Orange, 28.dp, 96.dp, 96.dp)
        StarAt(Yellow, 24.dp, -110.dp, 72.dp)
        StarAt(Violet, 16.dp, 6.dp, -124.dp)
        Dot(Brand2, 12.dp, -70.dp, 116.dp)
        Dot(Pink, 9.dp, 124.dp, 18.dp)
    }
}

@Composable
private fun BoxScope.StarAt(color: Color, size: Dp, x: Dp, y: Dp) =
    Icon(Icons.Filled.Star, contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).offset(x, y).size(size))

@Composable
private fun BoxScope.Dot(color: Color, size: Dp, x: Dp, y: Dp) =
    Box(Modifier.align(Alignment.Center).offset(x, y).size(size).background(color, CircleShape))

/** Bienvenida: logo, ingresar o crear cuenta, términos y franjas de color abajo. */
@Composable
fun WelcomeScreen(onLogin: () -> Unit, onCreate: () -> Unit) {
    var showTerms by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(SoftBackground)) {
        Ribbons(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.30f))
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(72.dp))
            Image(painterResource(R.drawable.logo_carrousel), contentDescription = "Carrousel", modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(215.dp))
            Spacer(Modifier.height(56.dp))
            PrimaryButton("Ingresar con mi cuenta", modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(), onClick = onLogin)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Crear una cuenta nueva", modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(), onClick = onCreate)
            Spacer(Modifier.height(28.dp))
            TextButton(onClick = { showTerms = true }) {
                Text("Ver términos y condiciones", color = MaterialTheme.colorScheme.onSurface, textDecoration = TextDecoration.Underline)
            }
            Spacer(Modifier.height(200.dp))
        }
    }
    if (showTerms) TermsDialog { showTerms = false }
}

/** Franjas onduladas con el degradado de marca y estrellas, como adorno inferior. */
@Composable
private fun Ribbons(modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            fun wave(y: Float, amp: Float) = Path().apply {
                moveTo(0f, y)
                cubicTo(w * 0.25f, y - amp, w * 0.45f, y + amp, w * 0.65f, y)
                cubicTo(w * 0.80f, y - amp * 0.8f, w * 0.92f, y - amp * 0.2f, w, y - amp * 0.4f)
            }
            drawPath(wave(h * 0.30f, h * 0.18f), BrandGradient, alpha = 0.30f, style = Stroke(width = 34f, cap = StrokeCap.Round))
            drawPath(wave(h * 0.58f, h * 0.22f), BrandGradient, alpha = 0.85f, style = Stroke(width = 44f, cap = StrokeCap.Round))
            drawPath(wave(h * 0.86f, h * 0.16f), Brush.horizontalGradient(listOf(Yellow, Orange, Pink)), alpha = 0.55f, style = Stroke(width = 30f, cap = StrokeCap.Round))
        }
        Icon(Icons.Filled.Star, null, tint = Yellow, modifier = Modifier.align(Alignment.TopStart).offset(28.dp, 10.dp).size(34.dp).alpha(0.9f))
        Icon(Icons.Filled.Star, null, tint = Pink, modifier = Modifier.align(Alignment.CenterEnd).offset((-30).dp, (-20).dp).size(28.dp))
        Icon(Icons.Filled.Star, null, tint = LogoGreen, modifier = Modifier.align(Alignment.BottomCenter).offset((-40).dp, (-24).dp).size(30.dp))
        Icon(Icons.Filled.Star, null, tint = Brand2, modifier = Modifier.align(Alignment.BottomEnd).offset((-60).dp, (-60).dp).size(22.dp).alpha(0.8f))
    }
}

/** Resumen de uso de datos. El texto legal definitivo lo debe aprobar Carrousel. */
@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } },
        title = { Text("Términos y condiciones") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("• Tu correo se usa para enviarte el código de acceso. No usamos contraseñas.")
                Text("• Las fechas de nacimiento que registres se usan solo para emitir los cupones de cumpleaños.")
                Text("• Cada cupón se canjea una sola vez en cualquier parque Carrousel, dentro de su vigencia y según sus condiciones.")
                Text("• Las ofertas y eventos pueden cambiar sin previo aviso y están sujetos a disponibilidad.")
                Text("• Puedes cerrar tu sesión en cualquier momento desde Perfil.")
            }
        },
    )
}
