package com.tyranor.runtime.input

import android.content.Context

/** Small module-local preferences for the pad's hide/show button placement. */
object PadOverlayPrefs {
    private const val PREFS = "tyranor_pad_overlay"
    private const val KEY_X = "fab_x"
    private const val KEY_Y = "fab_y"
    private const val KEY_VISIBLE = "pad_visible"

    private fun actionKey(actionId: String, axis: String) = "runtime_action_${actionId}_${axis}"

    fun fabPosition(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_X, 0.055f) to prefs.getFloat(KEY_Y, 0.48f)
    }

    fun isPadVisible(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VISIBLE, true)

    fun savePadVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VISIBLE, visible).apply()
    }

    fun saveFabPosition(context: Context, x: Float, y: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_X, x).putFloat(KEY_Y, y).apply()
    }

    fun runtimeActionPosition(context: Context, actionId: String): Pair<Float, Float>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val xKey = actionKey(actionId, "x")
        val yKey = actionKey(actionId, "y")
        if (!prefs.contains(xKey) || !prefs.contains(yKey)) return null
        return prefs.getFloat(xKey, 0f) to prefs.getFloat(yKey, 0f)
    }

    fun saveRuntimeActionPosition(context: Context, actionId: String, x: Float, y: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(actionKey(actionId, "x"), x.coerceIn(0f, 1f))
            .putFloat(actionKey(actionId, "y"), y.coerceIn(0f, 1f))
            .apply()
    }
}
