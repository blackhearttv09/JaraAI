package com.jara.ai.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

const val DEFAULT_TEXT_MODEL = "gemini-3.8-flash"
const val DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"

/** API key and settings live in AES-256 encrypted SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        "jara_secure",
        MasterKey.Builder(ctx.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v).apply()

    var textModel: String
        get() = sp.getString("text_model", DEFAULT_TEXT_MODEL)
            ?: DEFAULT_TEXT_MODEL
        set(v) = sp.edit().putString("text_model", v).apply()

    var liveModel: String
        get() = sp.getString("live_model", DEFAULT_LIVE_MODEL)
            ?: DEFAULT_LIVE_MODEL
        set(v) = sp.edit().putString("live_model", v).apply()

    var speed: Float
        get() = sp.getFloat("speed", 1.0f)
        set(v) = sp.edit().putFloat("speed", v).apply()

    var pitch: Float
        get() = sp.getFloat("pitch", 1.0f)
        set(v) = sp.edit().putFloat("pitch", v).apply()

    var screenControl: Boolean
        get() = sp.getBoolean("screen_control", false)
        set(v) = sp.edit().putBoolean("screen_control", v).apply()

    var history: String
        get() = sp.getString("history", "[]") ?: "[]"
        set(v) = sp.edit().putString("history", v).apply()

    var shareText: String
        get() = sp.getString(
            "share_text",
            "যারা এআই — আমার স্মার্ট AI সহকারী"
        ) ?: ""
        set(v) = sp.edit().putString("share_text", v).apply()
}
