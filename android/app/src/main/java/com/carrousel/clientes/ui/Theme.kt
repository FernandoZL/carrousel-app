package com.carrousel.clientes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta del Ecosistema Carrousel (la misma de CarrouselCumple, Helpdesk y Caja Chica: ecosystem-ui.css).
val Brand = Color(0xFF213A8F)
val BrandDark = Color(0xFF15255D)
val Brand2 = Color(0xFF2D4DB1)
val Violet = Color(0xFF6B46C1)
val Pink = Color(0xFFE62E7C)
val Orange = Color(0xFFF28B24)
val Yellow = Color(0xFFF4C430)
val Success = Color(0xFF067647)
val Warning = Color(0xFF9A6700)

/** Degradado de marca: la franja superior del ecosistema. */
val BrandGradient = Brush.horizontalGradient(listOf(Brand, Violet, Pink, Orange, Yellow))

/** Fondo del acceso, como .auth-page. */
val AuthGradient = Brush.linearGradient(listOf(BrandDark, Brand, Brand2))

private val Light = lightColorScheme(
    primary = Brand, onPrimary = Color.White, primaryContainer = Color(0xFFEFF8FF), onPrimaryContainer = Brand,
    secondary = Violet, onSecondary = Color.White, secondaryContainer = Color(0xFFF1ECFB), onSecondaryContainer = Color(0xFF3F2A75),
    tertiary = Pink, onTertiary = Color.White,
    background = Color(0xFFEEF2F7), onBackground = Color(0xFF1F2937),
    surface = Color.White, onSurface = Color(0xFF1F2937), surfaceVariant = Color(0xFFF4F6FA), onSurfaceVariant = Color(0xFF667085),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFF7F9FC),
    outline = Color(0xFF98A2B3), outlineVariant = Color(0xFFD0D5DD),
    error = Color(0xFFB42318), onError = Color.White, errorContainer = Color(0xFFFEF3F2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7FA7FF), onPrimary = Color(0xFF0B1A47), primaryContainer = Color(0xFF1D2836), onPrimaryContainer = Color(0xFF9DB9FF),
    secondary = Color(0xFFB79CF0), onSecondary = Color(0xFF26154F), secondaryContainer = Color(0xFF2A2140), onSecondaryContainer = Color(0xFFE6DCFA),
    tertiary = Color(0xFFFF7EB0),
    background = Color(0xFF0B0F17), onBackground = Color(0xFFF2F5F9),
    surface = Color(0xFF121821), onSurface = Color(0xFFF2F5F9), surfaceVariant = Color(0xFF18212E), onSurfaceVariant = Color(0xFF9EACBD),
    surfaceContainer = Color(0xFF121821), surfaceContainerLow = Color(0xFF121821), surfaceContainerHigh = Color(0xFF18212E),
    outline = Color(0xFF3A485C), outlineVariant = Color(0xFF2A3545),
    error = Color(0xFFFF8A80),
)

// Esquinas del ecosistema: controles de 9 px y tarjetas de 14 px.
private val EcosystemShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(9.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(18.dp),
)

@Composable
fun CarrouselTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, shapes = EcosystemShapes, content = content)
}

/** Franja de 4 dp con el degradado de marca, como .brand-strip. */
@Composable
fun BrandStrip(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(BrandGradient))
}

/** Verde y ámbar de estado, legibles en claro y en oscuro. */
@Composable
fun successColor() = if (isSystemInDarkTheme()) Color(0xFF6CE9A6) else Success

@Composable
fun warningColor() = if (isSystemInDarkTheme()) Color(0xFFFEC84B) else Warning
