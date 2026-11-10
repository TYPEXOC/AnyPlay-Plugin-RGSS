package com.tyranor.runtime.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tyranor.runtime.cheat.TyranorCheatController
import com.tyranor.runtime.cheat.TyranorCheatToggle
import com.tyranor.runtime.rgss.R

/** TYN-owned RGSS cheat panel. The controller talks to the Ruby runtime; this view owns the UI. */
class TyranorCheatPanel(
    private val activity: Activity,
    private val host: ViewGroup,
    private val manager: TyranorCheatController,
    private val primaryColor: Int,
    private val onPrimaryColor: Int,
    private val onExportDiagnostics: () -> Unit,
) {
    private enum class Tab { QUICK, CHARACTER, EXPLORATION, BATTLE }

    private var panel: View? = null
    private var content: LinearLayout? = null
    private var tabRow: LinearLayout? = null
    private var selectedTab = Tab.QUICK
    private var speedCheck: CheckBox? = null

    val isShowing: Boolean get() = panel != null

    fun show() {
        if (panel != null) return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(0x88000000.toInt())
            isClickable = true
            setOnClickListener { dismiss() }
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = rounded(0xFF191B21.toInt(), dp(18).toFloat())
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        val metrics = activity.resources.displayMetrics
        val width = (metrics.widthPixels * 0.88f).toInt().coerceAtMost(dp(560))
        val height = (metrics.heightPixels * 0.82f).toInt().coerceAtMost(dp(760))
        scrim.addView(card, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        card.addView(header())
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tabRow = tabs
        Tab.values().forEach { tab ->
            tabs.addView(tabButton(tab), LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        card.addView(tabs, LinearLayout.LayoutParams(-1, dp(46)))
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(10))
        }
        scroll.addView(content)
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        host.addView(scrim, ViewGroup.LayoutParams(-1, -1))
        panel = scrim
        renderTab()
    }

    fun handleBack(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    fun dismiss() {
        panel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        panel = null
        content = null
        tabRow = null
        speedCheck = null
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
    }

    private fun header(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val title = TextView(activity).apply {
            text = activity.getString(R.string.tyn_cheat_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val diagnostics = TextView(activity).apply {
            text = activity.getString(R.string.tyn_runtime_export_diagnostics)
            gravity = Gravity.CENTER
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                dismiss()
                onExportDiagnostics()
            }
        }
        addView(diagnostics, LinearLayout.LayoutParams(-2, dp(40)))
        val close = TextView(activity).apply {
            text = "×"
            contentDescription = activity.getString(R.string.tyn_cheat_close)
            gravity = Gravity.CENTER
            setTextColor(0xFFB9BEC8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setOnClickListener { dismiss() }
        }
        addView(close, LinearLayout.LayoutParams(dp(40), dp(40)))
    }

    private fun tabButton(tab: Tab): View = TextView(activity).apply {
        tag = tab
        text = when (tab) {
            Tab.QUICK -> activity.getString(R.string.tyn_cheat_tab_quick)
            Tab.CHARACTER -> activity.getString(R.string.tyn_cheat_tab_character)
            Tab.EXPLORATION -> activity.getString(R.string.tyn_cheat_tab_exploration)
            Tab.BATTLE -> activity.getString(R.string.tyn_cheat_tab_battle)
        }
        gravity = Gravity.CENTER
        setTextColor(if (selectedTab == tab) onPrimaryColor else 0xFF9FA5B2.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = if (selectedTab == tab) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        background = rounded(if (selectedTab == tab) primaryColor else Color.TRANSPARENT, dp(12).toFloat())
        setOnClickListener { selectedTab = tab; renderTab() }
    }

    private fun renderTab() {
        val target = content ?: return
        tabRow?.let { tabs ->
            for (index in 0 until tabs.childCount) {
                val node = tabs.getChildAt(index) as? TextView ?: continue
                val selected = node.tag == selectedTab
                node.setTextColor(if (selected) onPrimaryColor else 0xFF9FA5B2.toInt())
                node.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                node.background = rounded(if (selected) primaryColor else Color.TRANSPARENT, dp(12).toFloat())
            }
        }
        target.removeAllViews()
        speedCheck = null
        when (selectedTab) {
            Tab.QUICK -> {
                section(R.string.tyn_cheat_section_quick)
                toggle(TyranorCheatToggle.UNLIMITED_HPMP)
                toggle(TyranorCheatToggle.UNLIMITED_GOLD)
                toggle(TyranorCheatToggle.WALK_THROUGH_WALLS)
                toggle(TyranorCheatToggle.NO_ENCOUNTERS)
                toggle(TyranorCheatToggle.SPEED_BOOST)
                toggle(TyranorCheatToggle.FAST_TEXT)
                value(R.string.tyn_cheat_gold, "gold", false)
                value(R.string.tyn_cheat_speed, "speed", true, manager.speedMultiplier().toString())
            }
            Tab.CHARACTER -> {
                section(R.string.tyn_cheat_section_character)
                toggle(TyranorCheatToggle.UNLIMITED_HPMP)
                toggle(TyranorCheatToggle.UNLIMITED_TP)
                toggle(TyranorCheatToggle.STATUS_IMMUNITY)
                toggle(TyranorCheatToggle.UNLIMITED_ITEMS)
                value(R.string.tyn_cheat_gold, "gold", false)
                value(R.string.tyn_cheat_level, "level", false)
                value(R.string.tyn_cheat_exp, "exp", false)
                value(R.string.tyn_cheat_attack, "atk", false)
                value(R.string.tyn_cheat_defense, "def", false)
                value(R.string.tyn_cheat_magic_attack, "mat", false)
                value(R.string.tyn_cheat_magic_defense, "mdf", false)
                value(R.string.tyn_cheat_agility, "agi", false)
                value(R.string.tyn_cheat_luck, "luk", false)
                action(R.string.tyn_cheat_max_exp, "maxExp")
                action(R.string.tyn_cheat_full_inventory, "fullInventory")
            }
            Tab.EXPLORATION -> {
                section(R.string.tyn_cheat_section_exploration)
                toggle(TyranorCheatToggle.WALK_THROUGH_WALLS)
                toggle(TyranorCheatToggle.NO_ENCOUNTERS)
                toggle(TyranorCheatToggle.SPEED_BOOST)
                toggle(TyranorCheatToggle.FAST_TEXT)
                value(R.string.tyn_cheat_speed, "speed", true, manager.speedMultiplier().toString())
            }
            Tab.BATTLE -> {
                section(R.string.tyn_cheat_section_battle)
                toggle(TyranorCheatToggle.ONE_HIT_KILL)
                toggle(TyranorCheatToggle.INVINCIBILITY)
                toggle(TyranorCheatToggle.ALWAYS_CRIT)
                toggle(TyranorCheatToggle.ESCAPE_ALWAYS)
                toggle(TyranorCheatToggle.FREE_SKILL_COST)
                action(R.string.tyn_cheat_instant_win, "instantWin")
            }
        }
    }

    private fun section(resource: Int) {
        content?.addView(TextView(activity).apply {
            text = activity.getString(resource)
            setTextColor(0xFF9FA5B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(10), dp(4), dp(6))
        })
    }

    private fun toggle(toggle: TyranorCheatToggle) {
        val label = toggleLabel(toggle)
        val row = row()
        row.addView(TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val check = CheckBox(activity).apply {
            buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            isChecked = manager.isEnabled(toggle)
            if (toggle == TyranorCheatToggle.SPEED_BOOST) speedCheck = this
            setOnCheckedChangeListener { button, checked ->
                button.isEnabled = false
                manager.setEnabled(toggle, checked) { message ->
                    activity.runOnUiThread {
                        button.isEnabled = true
                        if (message.isNotEmpty()) {
                            button.setOnCheckedChangeListener(null)
                            button.isChecked = manager.isEnabled(toggle)
                            button.setOnCheckedChangeListener { view, enabled -> toggleChanged(toggle, view, enabled) }
                            toast(message)
                        }
                    }
                }
            }
        }
        row.addView(check)
        addRow(row)
    }

    private fun toggleChanged(toggle: TyranorCheatToggle, button: CompoundButton, checked: Boolean) {
        button.isEnabled = false
        manager.setEnabled(toggle, checked) { message ->
            activity.runOnUiThread {
                button.isEnabled = true
                if (message.isNotEmpty()) {
                    button.setOnCheckedChangeListener(null)
                    button.isChecked = manager.isEnabled(toggle)
                    button.setOnCheckedChangeListener { view, enabled -> toggleChanged(toggle, view, enabled) }
                    toast(message)
                }
            }
        }
    }

    private fun value(labelRes: Int, field: String, decimal: Boolean, initial: String = "") {
        val density = activity.resources.displayMetrics.density
        val row = row()
        row.orientation = LinearLayout.VERTICAL
        val label = TextView(activity).apply {
            text = activity.getString(labelRes)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        row.addView(label)
        val controls = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_value_hint)
            setText(initial)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF7B818D.toInt())
            textSize = 15f
            inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            background = rounded(0xFF282B33.toInt(), 10f * density)
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            minHeight = (44 * density).toInt()
        }
        controls.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button(R.string.tyn_cheat_apply) {
            manager.setValue(field, input.text.toString()) { result ->
                activity.runOnUiThread {
                    if (field == "speed" && result.startsWith("Speed x")) speedCheck?.isChecked = true
                    toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                }
            }
        }, LinearLayout.LayoutParams(dp(78), dp(42)).apply { marginStart = dp(8) })
        row.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        addRow(row)
    }

    private fun action(labelRes: Int, name: String) {
        val row = row()
        row.addView(TextView(activity).apply {
            text = activity.getString(labelRes)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(R.string.tyn_cheat_run) {
            manager.runAction(name) { result -> activity.runOnUiThread { toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) }) } }
        })
        addRow(row)
    }

    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(0xFF24272E.toInt(), 12f * activity.resources.displayMetrics.density)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun addRow(view: View) {
        content?.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun button(labelRes: Int, onClick: () -> Unit): View = TextView(activity).apply {
        text = activity.getString(labelRes)
        gravity = Gravity.CENTER
        setTextColor(onPrimaryColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(primaryColor, 10f * activity.resources.displayMetrics.density)
        setOnClickListener { onClick() }
    }

    private fun toggleLabel(toggle: TyranorCheatToggle): String = activity.getString(when (toggle) {
        TyranorCheatToggle.UNLIMITED_HPMP -> R.string.tyn_cheat_unlimited_hp_mp
        TyranorCheatToggle.UNLIMITED_TP -> R.string.tyn_cheat_unlimited_tp
        TyranorCheatToggle.UNLIMITED_GOLD -> R.string.tyn_cheat_unlimited_gold
        TyranorCheatToggle.STATUS_IMMUNITY -> R.string.tyn_cheat_status_immunity
        TyranorCheatToggle.UNLIMITED_ITEMS -> R.string.tyn_cheat_unlimited_items
        TyranorCheatToggle.WALK_THROUGH_WALLS -> R.string.tyn_cheat_noclip
        TyranorCheatToggle.NO_ENCOUNTERS -> R.string.tyn_cheat_no_encounters
        TyranorCheatToggle.SPEED_BOOST -> R.string.tyn_cheat_speed_boost
        TyranorCheatToggle.FAST_TEXT -> R.string.tyn_cheat_fast_text
        TyranorCheatToggle.ONE_HIT_KILL -> R.string.tyn_cheat_one_hit
        TyranorCheatToggle.INVINCIBILITY -> R.string.tyn_cheat_invincibility
        TyranorCheatToggle.ALWAYS_CRIT -> R.string.tyn_cheat_always_crit
        TyranorCheatToggle.ESCAPE_ALWAYS -> R.string.tyn_cheat_escape_always
        TyranorCheatToggle.FREE_SKILL_COST -> R.string.tyn_cheat_free_skill
    })

    private fun toast(message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
