package com.example.persiansubs

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var textSize: Int
        get() = sp.getInt("text_size", 20)
        set(value) = sp.edit().putInt("text_size", value).apply()

    var bgOpacity: Int
        get() = sp.getInt("bg_opacity", 70)
        set(value) = sp.edit().putInt("bg_opacity", value).apply()

    var autoMode: Boolean
        get() = sp.getBoolean("auto_mode", true)
        set(value) = sp.edit().putBoolean("auto_mode", value).apply()
}
