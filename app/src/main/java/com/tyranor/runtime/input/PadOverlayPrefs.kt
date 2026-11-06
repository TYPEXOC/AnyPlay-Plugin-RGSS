package com.tyranor.runtime.input

import android.content.Context

/** Small module-local preferences for the pad's hide/show button placement. */
object PadOverlayPrefs {
    private const val PREFS = "tyranor_pad_overlay"
    private const val KEY_X = "fab_x"
    private const val KEY_Y = "fab_y"
    private const val KEY_VISIBLE = "pad_visible"

    fun fabPosition(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_X, 0.055f) to prefs.getFloat(KEY_Y, 0.48f)
    }

    fun isPadVisible(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VISIBLE, true)

    fun saveFabPosition(context: Context, x: Float, y: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_X, x).putFloat(KEY_Y, y).apply()
    }
}
