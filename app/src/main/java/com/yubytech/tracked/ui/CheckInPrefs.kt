package com.yubytech.tracked.ui

import android.content.Context
import android.content.SharedPreferences

object CheckInPrefs {
    private const val PREFS_NAME = "checkin_prefs"
    private const val KEY_IS_CHECKED_IN = "is_checked_in"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_NAME = "client_name"
    private const val KEY_CLIENT_CONTACT = "client_contact"
    private const val KEY_CLIENT_LOAN_OFFICER = "client_loan_officer"
    private const val KEY_CLIENT_DB_ID = "client_db_id"
    private const val KEY_CHECKIN_LAT = "checkin_lat"
    private const val KEY_CHECKIN_LON = "checkin_lon"
    private const val KEY_CHECKOUT_LAT = "checkout_lat"
    private const val KEY_CHECKOUT_LON = "checkout_lon"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setCheckIn(context: Context, clientDbId: Int, clientId: String, clientName: String, clientContact: Long, clientLoanOfficer: String, lat: Double, lon: Double) {
        prefs(context).edit()
            .putInt(KEY_CLIENT_DB_ID, clientDbId)
            .putBoolean(KEY_IS_CHECKED_IN, true)
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_CLIENT_NAME, clientName)
            .putString(KEY_CLIENT_CONTACT, clientContact.toString())
            .putString(KEY_CLIENT_LOAN_OFFICER, clientLoanOfficer)
            .putString(KEY_CHECKIN_LAT, lat.toString())
            .putString(KEY_CHECKIN_LON, lon.toString())
            .remove(KEY_CHECKOUT_LAT)
            .remove(KEY_CHECKOUT_LON)
            .apply()
    }

    fun setCheckOut(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putBoolean(KEY_IS_CHECKED_IN, false)
            .putString(KEY_CHECKOUT_LAT, lat.toString())
            .putString(KEY_CHECKOUT_LON, lon.toString())
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun isCheckedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_CHECKED_IN, false)

    fun getClientId(context: Context): String? =
        prefs(context).getString(KEY_CLIENT_ID, null)

    fun getClientName(context: Context): String? =
        prefs(context).getString(KEY_CLIENT_NAME, null)

    fun getClientContact(context: Context): Long? =
        prefs(context).getString(KEY_CLIENT_CONTACT, null)?.toLongOrNull()

    fun getClientLoanOfficer(context: Context): String? =
        prefs(context).getString(KEY_CLIENT_LOAN_OFFICER, null)

    fun getClientDbId(context: Context): Int =
        prefs(context).getInt(KEY_CLIENT_DB_ID, 0)

    fun getCheckInLat(context: Context): Double? =
        prefs(context).getString(KEY_CHECKIN_LAT, null)?.toDoubleOrNull()

    fun getCheckInLon(context: Context): Double? =
        prefs(context).getString(KEY_CHECKIN_LON, null)?.toDoubleOrNull()

    fun getCheckOutLat(context: Context): Double? =
        prefs(context).getString(KEY_CHECKOUT_LAT, null)?.toDoubleOrNull()

    fun getCheckOutLon(context: Context): Double? =
        prefs(context).getString(KEY_CHECKOUT_LON, null)?.toDoubleOrNull()
} 