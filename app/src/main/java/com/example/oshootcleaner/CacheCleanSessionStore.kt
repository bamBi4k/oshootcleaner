package com.example.oshootcleaner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a CacheCleanSession so it survives process death. The user
 * may spend 30+ seconds in the Settings app clearing caches; if Android
 * kills our process while they're away, we should still be able to
 * restore the session when they come back.
 */
object CacheCleanSessionStore {

    private const val PREFS = "oh_shoot_prefs"
    private const val KEY_SESSION = "cache_clean_session_json"

    fun save(context: Context, session: CacheCleanSession) {
        val arr = JSONArray()
        session.targets.forEach { t ->
            arr.put(JSONObject().apply {
                put("pkg", t.packageName)
                put("label", t.label)
                put("bytes", t.cacheBytesAtStart)
                put("status", t.status.name)
            })
        }
        val root = JSONObject().apply {
            put("targets", arr)
            put("index", session.currentIndex)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION, root.toString())
            .apply()
    }

    fun restore(context: Context): CacheCleanSession? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null) ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("targets")
            val targets = mutableListOf<CacheCleanTarget>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                targets += CacheCleanTarget(
                    packageName = o.getString("pkg"),
                    label = o.getString("label"),
                    cacheBytesAtStart = o.getLong("bytes"),
                    status = runCatching {
                        CacheCleanTarget.Status.valueOf(o.getString("status"))
                    }.getOrDefault(CacheCleanTarget.Status.PENDING)
                )
            }
            // Build the session and set its starting index via the
            // internal API — never by assigning the property directly.
            CacheCleanSession(targets).also { s ->
                s.restoreIndex(root.optInt("index", 0))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION)
            .apply()
    }
}