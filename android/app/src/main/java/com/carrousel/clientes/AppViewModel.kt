package com.carrousel.clientes

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carrousel.clientes.data.Api
import com.carrousel.clientes.data.ApiException
import com.carrousel.clientes.data.Coupon
import com.carrousel.clientes.data.Event
import com.carrousel.clientes.data.Offer
import com.carrousel.clientes.data.Park
import com.carrousel.clientes.data.Profile
import com.carrousel.clientes.data.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val api = Api(BuildConfig.API_URL) { session.token }

    var loggedIn by mutableStateOf(session.token != null)
        private set
    var onboarded by mutableStateOf(session.onboarded)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // Acceso
    var email by mutableStateOf("")
    var challenge by mutableStateOf<String?>(null)
        private set

    // Datos
    var profile by mutableStateOf<Profile?>(null)
        private set
    var coupons by mutableStateOf<List<Coupon>>(emptyList())
        private set
    var offers by mutableStateOf<List<Offer>>(emptyList())
        private set
    var parks by mutableStateOf<List<Park>>(emptyList())
        private set
    var events by mutableStateOf<List<Event>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        if (loggedIn) refresh()
    }

    fun consumeMessage() { message = null }
    fun finishOnboarding() { session.onboarded = true; onboarded = true }

    /** Ejecuta una llamada mostrando el progreso; un 401 cierra la sesión local. */
    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                if (e.unauthorized && loggedIn) endSession()
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    private fun endSession() {
        session.token = null
        loggedIn = false
        loaded = false
        challenge = null
        profile = null
        coupons = emptyList()
        offers = emptyList()
    }

    fun requestCode() = run {
        val value = email.trim()
        challenge = api.requestCode(value)
        email = value
        message = "Te enviamos un código a $value."
    }

    fun changeEmail() { challenge = null }

    fun verify(code: String) = run {
        val c = challenge ?: return@run
        session.token = api.verifyCode(c, code.trim(), "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
        loggedIn = true
        load()
    }

    private suspend fun load() = coroutineScope {
        // Las llamadas son independientes: se piden a la vez; si una falla, se cancelan las demás.
        val p = async { api.profile() }
        val c = async { api.coupons() }
        val o = async { api.offers() }
        val pk = async { api.parks() }
        val ev = async { api.events() }
        profile = p.await()
        coupons = c.await()
        offers = o.await()
        parks = pk.await()
        events = ev.await()
        loaded = true
    }

    fun refresh() = run { load() }

    fun claim(offer: Offer, onReady: (Coupon) -> Unit) = run {
        val coupon = api.claimOffer(offer.id)
        coupons = api.coupons()
        offers = api.offers()
        onReady(coupon)
    }

    fun saveProfile(name: String, phone: String) = run {
        profile = api.saveProfile(name.trim(), phone.trim())
        message = "Datos guardados."
    }

    fun addMember(name: String, birthDate: String, onDone: () -> Unit) = run {
        val members = api.addMember(name.trim(), birthDate)
        profile = profile?.copy(members = members)
        coupons = api.coupons()
        message = "Festejado agregado."
        onDone()
    }

    fun logout() = run {
        try { api.logout() } catch (_: ApiException) { }
        endSession()
    }
}
