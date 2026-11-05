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
    companion object {
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
