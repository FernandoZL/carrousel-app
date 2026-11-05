package com.carrousel.clientes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.carrousel.clientes.ui.CarrouselTheme
import com.carrousel.clientes.ui.LoginScreen
import com.carrousel.clientes.ui.MainScreen
import com.carrousel.clientes.ui.OnboardingScreen
import com.carrousel.clientes.ui.WelcomeScreen
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 dibuja la app bajo la barra de estado: esto ajusta el color de la hora e íconos al tema.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CarrouselTheme {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(vm.message) {
                    vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
                }
                // Primer inicio: presentación → bienvenida → correo (ingresar o crear cuenta).
                var entry by rememberSaveable { mutableStateOf<String?>(null) }
                when {
                    vm.loggedIn -> MainScreen(vm, snackbar)
                    !vm.onboarded -> OnboardingScreen(onDone = vm::finishOnboarding)
                    entry == null -> WelcomeScreen(onLogin = { entry = "login" }, onCreate = { entry = "create" })
                    else -> Box(Modifier.fillMaxSize()) {
                        val back = { vm.changeEmail(); entry = null }
                        BackHandler(onBack = back)
                        LoginScreen(vm, newAccount = entry == "create", onBack = back)
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                    }
                }
            }
        }
    }
}
