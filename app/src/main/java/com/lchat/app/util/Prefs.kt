package com.lchat.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors

object Prefs {

    private const val FILE = "lchat_prefs_v2"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_LAST_EMAIL = "last_email"
    private const val KEY_LAST_PASSWORD = "last_password"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isDarkMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK, enabled).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun saveAccount(context: Context, email: String, password: String) {
        val p = prefs(context)
        val accounts = p.getStringSet(KEY_ACCOUNTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        accounts.add(email)
        p.edit()
            .putStringSet(KEY_ACCOUNTS, accounts)
            .putString(KEY_LAST_EMAIL, email)
            .putString(KEY_LAST_PASSWORD, password)
            .apply()
    }

    fun lastEmail(context: Context): String? = prefs(context).getString(KEY_LAST_EMAIL, null)
    fun lastPassword(context: Context): String? = prefs(context).getString(KEY_LAST_PASSWORD, null)

    fun accounts(context: Context): List<String> =
        prefs(context).getStringSet(KEY_ACCOUNTS, emptySet())?.toList() ?: emptyList()

    fun removeAccount(context: Context, email: String) {
        val p = prefs(context)
        val accounts = p.getStringSet(KEY_ACCOUNTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        accounts.remove(email)
        p.edit().putStringSet(KEY_ACCOUNTS, accounts).apply()
        if (lastEmail(context) == email) {
            p.edit().remove(KEY_LAST_EMAIL).remove(KEY_LAST_PASSWORD).apply()
        }
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}