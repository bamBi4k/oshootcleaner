package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * Persists the user's app-language choice and applies it. On API 33+ we
 * use the platform per-app language API. Below that we fall back to a
 * configuration override applied in MainActivity.attachBaseContext().
 */
object LanguageStore {
    private const val PREFS = "oh_shoot_prefs"
    private const val KEY = "app_language"

    const val SYSTEM = "system"

    data class Choice(val tag: String, val displayName: String)

    val choices = listOf(
        Choice(SYSTEM, "System default"),
        Choice("en", "English"),
        Choice("de", "Deutsch"),
        Choice("es", "Español"),
        Choice("fr", "Français"),
        Choice("it", "Italiano"),
        Choice("pt", "Português"),
        Choice("ru", "Русский")
    )

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, tag).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            lm.applicationLocales = if (tag == SYSTEM) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
        }
    }
}