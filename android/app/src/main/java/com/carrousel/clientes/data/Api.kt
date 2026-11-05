package com.carrousel.clientes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error con el mensaje que da el servidor (ya en español) y su código HTTP; 0 si no hubo conexión. */
class ApiException(message: String, val status: Int) : Exception(message) {
    val unauthorized get() = status == 401
}

/** Cliente de server/public/api.php. El token de la sesión va en X-App-Token; no se usan cookies. */
class Api(private val baseUrl: String, private val token: () -> String?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(action: String, body: JSONObject?): Any? = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("action", action).build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
        token()?.let { request.header("X-App-Token", it) }
        if (body != null) request.post(body.toString().toRequestBody(jsonType))
        try {
            client.newCall(request.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    throw ApiException("El servidor respondió algo inesperado (${response.code}).", response.code)
                }
                if (!json.optBoolean("ok")) throw ApiException(json.optString("error", "No se pudo completar la operación."), response.code)
                json.opt("data")
            }
        } catch (e: IOException) {
            throw ApiException("No hay conexión con Carrousel. Revisa tu internet e inténtalo de nuevo.", 0)
        }
    }

    private suspend fun obj(action: String, body: JSONObject? = null) = call(action, body) as JSONObject
    private suspend fun arr(action: String, body: JSONObject? = null) = call(action, body) as JSONArray

    suspend fun requestCode(email: String): String = obj("code-request", JSONObject().put("email", email)).getString("challenge")

    /** Devuelve el token de la nueva sesión. */
    suspend fun verifyCode(challenge: String, code: String, device: String): String =
        obj("code-verify", JSONObject().put("challenge", challenge).put("code", code).put("device", device)).getString("token")

    suspend fun logout() { call("logout", JSONObject()) }
    suspend fun profile() = Profile.from(obj("profile"))
    suspend fun saveProfile(name: String, phone: String) = Profile.from(obj("profile-save", JSONObject().put("name", name).put("phone", phone)))
    suspend fun addMember(name: String, birthDate: String) =
        arr("member-add", JSONObject().put("name", name).put("birth_date", birthDate)).mapObjects { Member.from(it) }
    suspend fun coupons() = arr("coupons").mapObjects { Coupon.from(it) }
    suspend fun offers() = arr("offers").mapObjects { Offer.from(it) }
    suspend fun claimOffer(id: Int) = Coupon.from(obj("offer-claim", JSONObject().put("offer_id", id)))
    suspend fun parks() = arr("parks").mapObjects { Park.from(it) }
    suspend fun events() = arr("events").mapObjects { Event.from(it) }
}
