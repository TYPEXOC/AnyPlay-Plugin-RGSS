package com.tyranor.runtime.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    private val onEditPad: () -> Unit,
) {
    private enum class Tab { QUICK, ITEMS, CHARACTER, SYSTEM, MAP, BATTLE, SAVE, KEYS, ABOUT }

    private var panel: View? = null
    private var content: LinearLayout? = null
    private var currentCard: LinearLayout? = null
    private var tabRow: LinearLayout? = null
    private var tabScroll: HorizontalScrollView? = null
    private var selectedTab = Tab.QUICK
    private var speedCheck: CheckBox? = null
    private val darkMode =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val panelColor = if (darkMode) 0xFF171A18.toInt() else 0xFFF7F8F7.toInt()
    private val surfaceColor = if (darkMode) 0xFF202522.toInt() else Color.WHITE
    private val inputColor = if (darkMode) 0xFF171B19.toInt() else Color.WHITE
    private val contentColor = if (darkMode) 0xFF171A18.toInt() else 0xFFF3F5F3.toInt()
    private val rowColor = if (darkMode) 0xFF292F2B.toInt() else 0xFFF6F8F6.toInt()
    private val primaryTextColor = if (darkMode) 0xFFEEF3F0.toInt() else 0xFF17211D.toInt()
    private val secondaryTextColor = if (darkMode) 0xFFAAB4AE.toInt() else 0xFF66736C.toInt()
    private val borderColor = if (darkMode) 0xFF343C37.toInt() else 0xFFDCE2DE.toInt()

    val isShowing: Boolean get() = panel != null

    fun show() {
        if (panel != null) return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            setOnClickListener { dismiss() }
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = rounded(panelColor, dp(18).toFloat())
            clipToOutline = true
        }
        val metrics = activity.resources.displayMetrics
        val width = (metrics.widthPixels * 0.94f).toInt().coerceAtMost(dp(820))
        val height = (metrics.heightPixels * 0.90f).toInt().coerceAtMost(dp(720))
        card.elevation = dp(12).toFloat()
        scrim.addView(card, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        card.addView(header())
        card.addView(View(activity).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tabRow = tabs
        val tabsScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(tabs, ViewGroup.LayoutParams(-2, -1))
        }
        tabScroll = tabsScroll
        tabs.setPadding(dp(8), 0, dp(8), 0)
        Tab.values().forEach { tab ->
            tabs.addView(tabButton(tab), LinearLayout.LayoutParams(-2, dp(48)))
        }
        card.addView(tabsScroll, LinearLayout.LayoutParams(-1, dp(48)))
        card.addView(View(activity).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content?.background = ColorDrawable(contentColor)
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
        currentCard = null
        tabRow = null
        tabScroll = null
        speedCheck = null
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
    }

    private fun header(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ColorDrawable(surfaceColor)
        setPadding(dp(14), dp(8), dp(10), dp(8))
        val title = TextView(activity).apply {
            text = activity.getString(R.string.tyn_cheat_title)
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }
        addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val runtimeBadge = TextView(activity).apply {
            text = activity.getString(R.string.tyn_cheat_runtime_badge)
            gravity = Gravity.CENTER
            setTextColor(if (darkMode) 0xFFC9D2CC.toInt() else 0xFF53605A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = rounded(if (darkMode) 0xFF39433D.toInt() else 0xFFE4EBE7.toInt(), dp(8).toFloat())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        addView(runtimeBadge, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        val close = TextView(activity).apply {
            text = "×"
            contentDescription = activity.getString(R.string.tyn_cheat_close)
            gravity = Gravity.CENTER
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            background = rounded(if (darkMode) 0xFF334039.toInt() else 0xFFE8EEEA.toInt(), dp(12).toFloat())
            setOnClickListener { dismiss() }
        }
        addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
    }

    private fun tabButton(tab: Tab): View = LinearLayout(activity).apply {
        tag = tab
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ColorDrawable(surfaceColor)
        minimumWidth = dp(64)
        addView(TextView(activity).apply {
            text = tabTitle(tab)
            gravity = Gravity.CENTER
            setTextColor(if (selectedTab == tab) primaryColor else secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = if (selectedTab == tab) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setPadding(dp(14), 0, dp(14), 0)
        }, LinearLayout.LayoutParams(-2, 0, 1f))
        addView(View(activity).apply {
            setBackgroundColor(if (selectedTab == tab) primaryColor else Color.TRANSPARENT)
        }, LinearLayout.LayoutParams(-1, dp(3)))
        setOnClickListener { selectedTab = tab; renderTab() }
    }

    private fun tabTitle(tab: Tab): String = when (tab) {
        Tab.QUICK -> activity.getString(R.string.tyn_cheat_tab_quick)
        Tab.ITEMS -> activity.getString(R.string.tyn_cheat_tab_items)
        Tab.CHARACTER -> activity.getString(R.string.tyn_cheat_tab_character)
        Tab.SYSTEM -> activity.getString(R.string.tyn_cheat_tab_system)
        Tab.MAP -> activity.getString(R.string.tyn_cheat_tab_map)
        Tab.BATTLE -> activity.getString(R.string.tyn_cheat_tab_battle)
        Tab.SAVE -> activity.getString(R.string.tyn_cheat_tab_save)
        Tab.KEYS -> activity.getString(R.string.tyn_cheat_tab_keys)
        Tab.ABOUT -> activity.getString(R.string.tyn_cheat_tab_about)
    }

    private fun renderTab() {
        val target = content ?: return
        tabRow?.let { tabs ->
            for (index in 0 until tabs.childCount) {
                val node = tabs.getChildAt(index) as? LinearLayout ?: continue
                val selected = node.tag == selectedTab
                (node.getChildAt(0) as? TextView)?.apply {
                    setTextColor(if (selected) primaryColor else secondaryTextColor)
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                node.getChildAt(1)?.setBackgroundColor(if (selected) primaryColor else Color.TRANSPARENT)
                if (selected) {
                    tabScroll?.post {
                        val left = node.left
                        val right = node.right
                        val viewportLeft = tabScroll?.scrollX ?: 0
                        val viewportRight = viewportLeft + (tabScroll?.width ?: 0)
                        if (left < viewportLeft) tabScroll?.smoothScrollTo(left, 0)
                        else if (right > viewportRight) tabScroll?.smoothScrollTo(right - (tabScroll?.width ?: 0), 0)
                    }
                }
            }
        }
        target.removeAllViews()
        currentCard = null
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
            Tab.ITEMS -> renderItems()
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
            Tab.SYSTEM -> {
                section(R.string.tyn_cheat_section_system)
                note(R.string.tyn_cheat_system_unavailable)
            }
            Tab.MAP -> {
                section(R.string.tyn_cheat_section_exploration)
                note(R.string.tyn_cheat_map_unavailable)
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
            Tab.SAVE -> {
                section(R.string.tyn_cheat_section_save)
                note(R.string.tyn_cheat_save_unavailable)
            }
            Tab.KEYS -> {
                section(R.string.tyn_cheat_section_keys)
                note(R.string.tyn_cheat_keys_note)
                action(R.string.tyn_cheat_section_keys, "__editPad", R.string.tyn_cheat_edit_pad)
            }
            Tab.ABOUT -> {
                section(R.string.tyn_cheat_section_about)
                note(R.string.tyn_cheat_about_note)
                action(R.string.tyn_cheat_runtime_logs, "__exportDiagnostics", R.string.tyn_runtime_export_diagnostics)
            }
        }
    }

    private fun renderItems() {
        section(R.string.tyn_cheat_section_items)
        action(R.string.tyn_cheat_full_inventory, "fullInventory")
        val search = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_items_search)
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            textSize = 14f
            singleLine = true
            background = GradientDrawable().apply {
                setColor(inputColor)
                cornerRadius = 9f * activity.resources.displayMetrics.density
                setStroke(dp(1), if (darkMode) 0xFF4A554F.toInt() else 0xFFCBD5CF.toInt())
            }
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(44)
        }
        currentCard?.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        var availablePickables: List<TyranorCheatController.Pickable> = emptyList()
        var loaded = false
        fun renderPickables() {
            list.removeAllViews()
            if (!loaded) {
                list.addView(noteView(R.string.tyn_cheat_items_loading))
                return
            }
            val query = search.text.toString().trim()
            val filtered = availablePickables.asSequence()
                .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.key.contains(query, ignoreCase = true) }
                .take(100)
                .toList()
            if (filtered.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_items_empty))
                return
            }
            filtered.forEach { item ->
                val itemRow = row()
                itemRow.addView(TextView(activity).apply {
                    text = "${item.name}  #${item.key}"
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, -2, 1f))
                itemRow.addView(button(R.string.tyn_cheat_give) {
                    manager.give(item.kind, item.key, "1") { result ->
                        activity.runOnUiThread {
                            toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                        }
                    }
                })
                list.addView(itemRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = renderPickables()
        })
        manager.listPickables { result ->
            if (panel == null || selectedTab != Tab.ITEMS) return@listPickables
            availablePickables = result
            loaded = true
            renderPickables()
        }
    }

    private fun note(resource: Int) {
        val destination = currentCard ?: content ?: return
        destination.addView(noteView(resource), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
    }

    private fun noteView(resource: Int) = TextView(activity).apply {
        text = activity.getString(resource)
        setTextColor(secondaryTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun section(resource: Int) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = 14f * activity.resources.displayMetrics.density
                setStroke(dp(1), if (darkMode) 0xFF343C37.toInt() else 0xFFE0E5E2.toInt())
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        card.addView(TextView(activity).apply {
            text = activity.getString(resource)
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(6))
        })
        content?.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        currentCard = card
    }

    private fun toggle(toggle: TyranorCheatToggle) {
        val label = toggleLabel(toggle)
        val row = row()
        row.addView(TextView(activity).apply {
            text = label
            setTextColor(primaryTextColor)
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
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        row.addView(label)
        val controls = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_value_hint)
            setText(initial)
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            background = GradientDrawable().apply {
                setColor(inputColor)
                cornerRadius = 9f * density
                setStroke(dp(1), if (darkMode) 0xFF4A554F.toInt() else 0xFFCBD5CF.toInt())
            }
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

    private fun action(labelRes: Int, name: String, buttonLabelRes: Int = R.string.tyn_cheat_run) {
        val row = row()
        row.addView(TextView(activity).apply {
            text = activity.getString(labelRes)
            setTextColor(primaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(buttonLabelRes, primary = name == "__editPad") {
            if (name == "__editPad") {
                dismiss()
                onEditPad()
            } else if (name == "__exportDiagnostics") {
                dismiss()
                onExportDiagnostics()
            } else {
                manager.runAction(name) { result -> activity.runOnUiThread { toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) }) } }
            }
        })
        addRow(row)
    }

    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(rowColor, 10f * activity.resources.displayMetrics.density)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun addRow(view: View) {
        val destination = currentCard ?: content ?: return
        destination.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
    }

    private fun button(labelRes: Int, primary: Boolean = false, onClick: () -> Unit): View = TextView(activity).apply {
        text = activity.getString(labelRes)
        gravity = Gravity.CENTER
        setTextColor(if (primary) onPrimaryColor else if (darkMode) 0xFFB9EADC.toInt() else 0xFF165F51.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(8), dp(12), dp(8))
        val color = when {
            primary && darkMode -> 0xFF4FB59A.toInt()
            primary -> primaryColor
            darkMode -> 0xFF334039.toInt()
            else -> 0xFFE6EFEB.toInt()
        }
        background = rounded(color, 10f * activity.resources.displayMetrics.density)
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
