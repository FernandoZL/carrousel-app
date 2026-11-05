package com.carrousel.clientes.data

import android.content.Context

/**
 * Token de la sesión en las preferencias privadas de la app (allowBackup=false, no sale del teléfono). El servidor
 * guarda solo su HMAC, vence a los 90 días y se revoca al cerrar sesión.
 */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().apply { if (value == null) remove("token") else putString("token", value) }.apply()
    /** La presentación de bienvenida se muestra solo la primera vez. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit().putBoolean("onboarded", value).apply()
}
