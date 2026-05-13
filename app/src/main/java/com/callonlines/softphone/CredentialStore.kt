package com.callonlines.softphone

import android.content.Context
import android.content.SharedPreferences

class CredentialStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cl_sip_creds", Context.MODE_PRIVATE)

    fun save(user: String, pass: String, domain: String) {
        prefs.edit()
            .putString(KEY_USER, user)
            .putString(KEY_PASS, pass)
            .putString(KEY_DOMAIN, domain)
            .apply()
    }

    fun getUser(): String = prefs.getString(KEY_USER, "") ?: ""
    fun getPass(): String = prefs.getString(KEY_PASS, "") ?: ""
    fun getDomain(): String = prefs.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
    fun has(): Boolean = getUser().isNotEmpty() && getPass().isNotEmpty()
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_USER = "user"
        const val KEY_PASS = "pass"
        const val KEY_DOMAIN = "domain"
        const val DEFAULT_DOMAIN = "sip.callonlines.com"
    }
}
