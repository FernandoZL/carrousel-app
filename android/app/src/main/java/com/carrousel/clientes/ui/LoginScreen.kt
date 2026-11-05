package com.carrousel.clientes.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrousel.clientes.AppViewModel
import com.carrousel.clientes.R

/** Acceso como el del ecosistema (.auth-page): degradado azul, tarjeta con logo, título y código de 6 dígitos. */
@Composable
fun LoginScreen(vm: AppViewModel, newAccount: Boolean = false, onBack: () -> Unit = {}) {
    var code by rememberSaveable { mutableStateOf("") }
    val waitingCode = vm.challenge != null
    Box(Modifier.fillMaxSize().background(AuthGradient).systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
            ) {
                BrandStrip()
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                }
                Column(Modifier.padding(start = 26.dp, end = 26.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(7.dp)) {
                        Image(painterResource(R.drawable.logo_carrousel), contentDescription = "Carrousel", modifier = Modifier.widthIn(max = 220.dp).height(105.dp))
                    }
                    Text(if (waitingCode) "Revisa tu correo" else if (newAccount) "Crea tu cuenta" else "Ingresa a tu cuenta", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        if (waitingCode) "Escribe el código de 6 dígitos que enviamos a ${vm.email}. Vence en 10 minutos."
                        else if (newAccount) "Escribe tu correo y te enviaremos un código para crear tu cuenta. No necesitas contraseña."
                        else "Escribe el correo de tu cuenta y te enviaremos un código de acceso. No necesitas contraseña.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!waitingCode) {
                        OutlinedTextField(
                            value = vm.email, onValueChange = { vm.email = it }, label = { Text("Correo electrónico") },
                            placeholder = { Text("nombre@correo.com") }, singleLine = true, shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { vm.requestCode() }), modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton("Enviar código de acceso", busy = vm.busy, enabled = vm.email.contains('@')) { vm.requestCode() }
                    } else {
                        OutlinedTextField(
                            value = code, onValueChange = { v -> code = v.filter(Char::isDigit).take(6) }, singleLine = true,
                            placeholder = { Text("000000", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (code.length == 6) vm.verify(code) }),
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, letterSpacing = 10.sp, textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton("Validar código", busy = vm.busy, enabled = code.length == 6) { vm.verify(code) }
                        TextButton(onClick = { code = ""; vm.requestCode() }, enabled = !vm.busy) { Text("Enviar otro código") }
                        TextButton(onClick = { code = ""; vm.changeEmail() }) { Text("Usar otro correo") }
                    }
                }
            }
        }
    }
}

/** Botón principal del ecosistema: azul de marca, 48 dp de alto y esquinas de 9 dp. */
@Composable
fun PrimaryButton(text: String, busy: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, shape = MaterialTheme.shapes.small, modifier = modifier.height(48.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}
