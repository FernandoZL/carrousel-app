package com.carrousel.clientes.data

import org.json.JSONArray
import org.json.JSONObject

/** Texto o null: optString devuelve "null" para un valor JSON null. */
fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)
fun JSONObject.dbl(key: String): Double? = if (isNull(key)) null else optDouble(key)
fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { block(getJSONObject(it)) }

enum class CouponStatus(val label: String) {
    AVAILABLE("Disponible"), UPCOMING("Aún no disponible"), REDEEMED("Canjeado"), EXPIRED("Vencido"), VOID("Anulado");

    companion object {
        fun from(value: String?) = when (value) {
            "available" -> AVAILABLE
            "upcoming" -> UPCOMING
            "redeemed" -> REDEEMED
            "expired" -> EXPIRED
            else -> VOID
        }
    }
}

data class Coupon(
    val id: Long,
    val kind: String,
    val code: String,
    val label: String,
    val terms: String,
    val validFrom: String,
    val validUntil: String,
    val status: CouponStatus,
    val memberName: String?,
    val offerTitle: String?,
    val birthdayYear: Int?,
    val redeemedAt: String?,
    val redeemedPark: String?,
) {
    val isBirthday get() = kind == "birthday"
    val title get() = if (isBirthday) "Cumpleaños de ${memberName.orEmpty()}" else offerTitle.orEmpty()

    companion object {
        fun from(o: JSONObject) = Coupon(
            id = o.getLong("id"), kind = o.getString("kind"), code = o.getString("code"), label = o.getString("label"),
            terms = o.optString("terms"), validFrom = o.getString("valid_from"), validUntil = o.getString("valid_until"),
            status = CouponStatus.from(o.str("status")), memberName = o.str("member_name"), offerTitle = o.str("offer_title"),
            birthdayYear = if (o.isNull("birthday_year")) null else o.getInt("birthday_year"),
            redeemedAt = o.str("redeemed_at"), redeemedPark = o.str("redeemed_park"),
        )
    }
}

data class Offer(
    val id: Int,
    val title: String,
    val description: String,
    val parkName: String?,
    val startsOn: String,
    val endsOn: String,
    val couponEnabled: Boolean,
    val couponLabel: String?,
    val couponTerms: String?,
    val claimedCode: String?,
) {
    companion object {
        fun from(o: JSONObject) = Offer(
            id = o.getInt("id"), title = o.getString("title"), description = o.getString("description"), parkName = o.str("park_name"),
            startsOn = o.getString("starts_on"), endsOn = o.getString("ends_on"), couponEnabled = o.getBoolean("coupon_enabled"),
            couponLabel = o.str("coupon_label"), couponTerms = o.str("coupon_terms"), claimedCode = o.str("claimed_code"),
        )
    }
}

data class Park(
    val id: Int,
    val name: String,
    val address: String,
    val hours: String,
    val phone: String,
    val latitude: Double?,
    val longitude: Double?,
) {
    val isKiddies get() = name.startsWith("Kiddies", ignoreCase = true)

    /** Coordenadas del parque (usa las de la base o la referencia conocida en Guatemala si aún no se cargaron). */
    val effectiveLat: Double? get() = latitude ?: defaultCoords[name]?.first
    val effectiveLon: Double? get() = longitude ?: defaultCoords[name]?.second

    companion object {
        private val defaultCoords = mapOf(
            "Alturas" to (15.3197 to -91.4724),
            "Andaria Chimaltenango" to (14.6592 to -90.8128),
            "Cayalá" to (14.6098 to -90.4864),
            "Celajes" to (15.0258 to -91.1485),
            "Centro Comercial Florida" to (14.6475 to -90.5694),
            "El Frutal" to (14.5186 to -90.5732),
            "El Gran Carchá" to (15.4754 to -90.3115),
            "Interplaza Escuintla" to (14.3168 to -90.7836),
            "Interplaza Xela" to (14.8631 to -91.5472),
            "Kiddies Centra Norte" to (14.6612 to -90.4538),
            "Kiddies Megacentro" to (14.6258 to -90.5624),
            "Kiddies Pradera Palín" to (14.4052 to -90.6968),
            "La Trinidad Coatepeque" to (14.7024 to -91.8632),
            "La Trinidad Reu" to (14.5362 to -91.6778),
            "Los Altos" to (14.9122 to -91.3612),
            "Metrocentro" to (14.5256 to -90.5882),
            "Metroplaza Jutiapa" to (14.2825 to -89.8924),
            "Plaza Américas" to (14.5328 to -91.5086),
            "Plaza Magdalena" to (15.4692 to -90.3768),
            "Plaza San Francisco" to (14.6345 to -89.9852),
            "Pradera Chimaltenango" to (14.6614 to -90.8195),
            "Pradera Chiquimula" to (14.7924 to -89.5442),
            "Pradera Escuintla" to (14.3012 to -90.7858),
            "Pradera Huehuetenango" to (15.3145 to -91.4945),
            "Pradera Puerto Barrios" to (15.7154 to -88.5872),
            "Pradera Zacapa" to (14.9722 to -89.5278),
            "Santa Clara" to (14.5284 to -90.5945),
            "Santalu" to (14.3312 to -91.0254),
            "Telares" to (14.5478 to -90.7385),
            "Tikal" to (14.6231 to -90.5532),
            "Vistares" to (14.5812 to -90.5495),
        )

        fun from(o: JSONObject) = Park(
            id = o.getInt("id"), name = o.getString("name"), address = o.optString("address"), hours = o.optString("hours"),
            phone = o.optString("phone"), latitude = o.dbl("latitude"), longitude = o.dbl("longitude"),
        )
    }
}

data class Event(
    val id: Int,
    val title: String,
    val description: String,
    val parkId: Int?,
    val parkName: String?,
    val startsAt: String,
    val endsAt: String?,
) {
    companion object {
        fun from(o: JSONObject) = Event(
            id = o.getInt("id"), title = o.getString("title"), description = o.getString("description"),
            parkId = if (o.isNull("park_id")) null else o.getInt("park_id"), parkName = o.str("park_name"), startsAt = o.getString("starts_at"), endsAt = o.str("ends_at"),
        )
    }
}

data class Member(val id: Long, val name: String, val birthDate: String) {
    companion object {
        fun from(o: JSONObject) = Member(o.getLong("id"), o.getString("name"), o.getString("birth_date"))
    }
}

data class Profile(val email: String, val name: String, val phone: String, val members: List<Member>, val maxMembers: Int) {
    companion object {
        fun from(o: JSONObject) = Profile(
            email = o.getString("email"), name = o.optString("name"), phone = o.optString("phone"),
            members = o.optJSONArray("members").mapObjects { Member.from(it) }, maxMembers = o.optInt("max_members", 8),
        )
    }
}
