package com.tyranor.runtime.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
    private val onResetPad: () -> Unit,
) {
    private enum class Tab { QUICK, ITEMS, CHARACTER, SYSTEM, MAP, BATTLE, SAVE, KEYS, ABOUT }

    private var panel: View? = null
    private var content: LinearLayout? = null
    private var currentCard: LinearLayout? = null
    private var tabRow: LinearLayout? = null
    private var tabScroll: HorizontalScrollView? = null
    private var bodyScroll: ScrollView? = null
    private var selectedTab = Tab.QUICK
    private var selectedMapEventId: Int? = null
    private var minimapEventSelected: ((Int) -> Unit)? = null
    private var currentGoldView: TextView? = null
    private var speedCheck: CheckBox? = null
    private var capabilities: TyranorCheatController.Capabilities? = null
    private var capabilitiesLoaded = false
    private var capabilityAttempts = 0
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
        bodyScroll = scroll
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
        requestCapabilities()
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
        bodyScroll = null
        selectedMapEventId = null
        minimapEventSelected = null
        currentGoldView = null
        speedCheck = null
        capabilities = null
        capabilitiesLoaded = false
        capabilityAttempts = 0
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
        minimapEventSelected = null
        currentGoldView = null
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
        if (!capabilitiesLoaded) {
            section(when (selectedTab) {
                Tab.SYSTEM -> R.string.tyn_cheat_section_system
                Tab.MAP -> R.string.tyn_cheat_section_exploration
                Tab.SAVE -> R.string.tyn_cheat_section_save
                else -> R.string.tyn_cheat_section_quick
            })
            note(R.string.tyn_cheat_capabilities_loading)
            return
        }
        when (selectedTab) {
            Tab.QUICK -> {
                section(R.string.tyn_cheat_section_quick)
                toggle(TyranorCheatToggle.INVINCIBILITY)
                toggle(TyranorCheatToggle.ONE_HIT_KILL)
                toggle(TyranorCheatToggle.ALWAYS_CRIT)
                toggle(TyranorCheatToggle.UNLIMITED_HPMP)
                toggle(TyranorCheatToggle.UNLIMITED_GOLD)
                toggle(TyranorCheatToggle.WALK_THROUGH_WALLS)
                toggle(TyranorCheatToggle.NO_ENCOUNTERS)
                toggle(TyranorCheatToggle.SPEED_BOOST)
                toggle(TyranorCheatToggle.FAST_TEXT)
                toggle(TyranorCheatToggle.EVENT_SPEED)
                toggle(TyranorCheatToggle.MESSAGE_SKIP)
                if (capabilities?.values?.contains("gold") == true) showCurrentGold()
                value(R.string.tyn_cheat_gold, "gold", false)
                if (capabilities?.values?.contains("gold") == true) action(R.string.tyn_cheat_max_gold, "maxGold")
                if (capabilities?.features?.let { "actors" in it || "pokemonParty" in it } == true) {
                    action(R.string.tyn_cheat_recover_party, "recoverParty")
                }
                action(R.string.tyn_cheat_refresh, "__refresh", R.string.tyn_cheat_refresh)
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
                value(if (capabilities?.engine == "vx") R.string.tyn_cheat_spirit else R.string.tyn_cheat_magic_attack,
                    "mat", false)
                value(R.string.tyn_cheat_magic_defense, "mdf", false)
                value(R.string.tyn_cheat_agility, "agi", false)
                value(R.string.tyn_cheat_luck, "luk", false)
                value(R.string.tyn_cheat_dexterity, "dex", false)
                action(R.string.tyn_cheat_max_exp, "maxExp")
                action(R.string.tyn_cheat_full_inventory, "fullInventory")
                action(R.string.tyn_cheat_recover_party, "recoverParty")
                if (capabilities?.features?.contains("pokemonParty") == true) renderPokemonList()
                else renderActorList()
            }
            Tab.SYSTEM -> renderSystem()
            Tab.MAP -> {
                renderMap()
                if (capabilities?.features?.contains("minimap") == true) renderMiniMap()
                if (capabilities?.features?.contains("events") == true) renderEvents()
                section(R.string.tyn_cheat_section_exploration)
                toggle(TyranorCheatToggle.WALK_THROUGH_WALLS)
                toggle(TyranorCheatToggle.NO_ENCOUNTERS)
                toggle(TyranorCheatToggle.SPEED_BOOST)
                toggle(TyranorCheatToggle.FAST_TEXT)
                toggle(TyranorCheatToggle.EVENT_SPEED)
                toggle(TyranorCheatToggle.MESSAGE_SKIP)
                value(R.string.tyn_cheat_speed, "speed", true, manager.speedMultiplier().toString())
            }
            Tab.BATTLE -> {
                renderBattle()
            }
            Tab.SAVE -> {
                section(R.string.tyn_cheat_section_save)
                note(R.string.tyn_cheat_save_native_note)
                action(R.string.tyn_cheat_open_save, "saveScreen")
                action(R.string.tyn_cheat_open_load, "loadScreen")
                if (capabilities?.features?.contains("saveSlots") == true) renderSaveSlots()
            }
            Tab.KEYS -> {
                section(R.string.tyn_cheat_section_keys)
                note(R.string.tyn_cheat_keys_note)
                action(R.string.tyn_cheat_section_keys, "__editPad", R.string.tyn_cheat_edit_pad)
                action(R.string.tyn_cheat_reset_pad, "__resetPad", R.string.tyn_cheat_reset_pad)
            }
            Tab.ABOUT -> {
                section(R.string.tyn_cheat_section_about)
                note(R.string.tyn_cheat_about_note)
                note(R.string.tyn_cheat_about_compat_note)
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
            isSingleLine = true
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
                .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) ||
                    it.key.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
                .take(200)
                .toList()
            if (filtered.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_items_empty))
                return
            }
            filtered.forEach { item ->
                val itemRow = row()
                itemRow.addView(TextView(activity).apply {
                    text = activity.getString(R.string.tyn_cheat_item_count_format, item.name, item.key, item.quantity)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, -2, 1f))
                itemRow.addView(button(R.string.tyn_cheat_give) {
                    manager.give(item.kind, item.key, "1") { result ->
                        activity.runOnUiThread {
                            toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                            manager.listPickables { updated ->
                                if (panel != null && selectedTab == Tab.ITEMS) {
                                    availablePickables = updated
                                    renderPickables()
                                }
                            }
                        }
                    }
                })
                if (item.kind != "pokemon") {
                    itemRow.addView(button(R.string.tyn_cheat_quantity) { editItemQuantity(item) },
                        LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(5) })
                }
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

    private fun renderActorList() {
        if (capabilities?.features?.contains("actors") != true) return
        section(R.string.tyn_cheat_actor_title)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        list.addView(noteView(R.string.tyn_cheat_actor_loading))
        manager.listActors { actors ->
            if (panel == null || selectedTab != Tab.CHARACTER) return@listActors
            list.removeAllViews()
            if (actors.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_actor_empty))
                return@listActors
            }
            actors.forEach { actor ->
                val block = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(rowColor, 10f * activity.resources.displayMetrics.density)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                block.addView(TextView(activity).apply {
                    val summary = if (capabilities?.engine == "xp") R.string.tyn_cheat_actor_summary_xp
                        else R.string.tyn_cheat_actor_summary
                    text = activity.getString(summary, actor.name, actor.level,
                        actor.hp, actor.maxHp, actor.mp, actor.maxMp)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
                val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                actions.addView(button(R.string.tyn_cheat_edit) { editActor(actor) }, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(button(R.string.tyn_cheat_recover) {
                    manager.recoverActor(actor.id) { result -> activity.runOnUiThread { toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) }) } }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                block.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                val collectionActions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                if (capabilities?.features?.contains("actorSkills") == true) {
                    collectionActions.addView(button(R.string.tyn_cheat_skills) { editActorCollection(actor.id, "skills") },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                }
                if (capabilities?.features?.contains("actorStates") == true) {
                    collectionActions.addView(button(R.string.tyn_cheat_states) { editActorCollection(actor.id, "states") },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                }
                if (capabilities?.features?.contains("actorEquips") == true) {
                    collectionActions.addView(button(R.string.tyn_cheat_equipment) { editActorEquips(actor.id) },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                }
                if (collectionActions.childCount > 0) {
                    block.addView(collectionActions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                }
                list.addView(block, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
        }
    }

    private fun renderPokemonList() {
        section(R.string.tyn_cheat_pokemon_party)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        list.addView(noteView(R.string.tyn_cheat_actor_loading))
        manager.listPokemon { party ->
            if (panel == null || selectedTab != Tab.CHARACTER) return@listPokemon
            list.removeAllViews()
            if (party.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_pokemon_party_empty))
                return@listPokemon
            }
            party.forEach { pokemon ->
                val block = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(rowColor, 10f * activity.resources.displayMetrics.density)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                block.addView(TextView(activity).apply {
                    text = activity.getString(R.string.tyn_cheat_pokemon_summary, pokemon.index + 1, pokemon.name,
                        pokemon.level, pokemon.hp, pokemon.maxHp, pokemon.exp)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
                val pokemonDetails = mutableListOf<String>()
                if (pokemon.status.isNotBlank()) {
                    pokemonDetails.add(activity.getString(R.string.tyn_cheat_pokemon_current_status,
                        activity.getString(pokemonStatusLabel(pokemon.status))))
                }
                if (pokemon.heldItem.isNotBlank()) {
                    pokemonDetails.add(activity.getString(R.string.tyn_cheat_pokemon_current_item, pokemon.heldItem))
                }
                if (pokemonDetails.isNotEmpty()) block.addView(TextView(activity).apply {
                    text = pokemonDetails.joinToString(" · ")
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
                if (pokemon.isEgg) {
                    block.addView(noteView(R.string.tyn_cheat_pokemon_egg))
                } else {
                    val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    actions.addView(button(R.string.tyn_cheat_edit) { editPokemon(pokemon) },
                        LinearLayout.LayoutParams(0, -2, 1f))
                    actions.addView(button(R.string.tyn_cheat_recover) {
                        manager.recoverPokemon(pokemon.index) { result -> activity.runOnUiThread {
                            toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                            if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                        } }
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                    block.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

                    val pokemonActions = buildList<Pair<Int, () -> Unit>> {
                        val features = capabilities?.features.orEmpty()
                        if ("pokemonMoves" in features) add(R.string.tyn_cheat_skills to { editPokemonMoves(pokemon) })
                        if ("pokemonStatus" in features) add(R.string.tyn_cheat_states to { editPokemonStatus(pokemon) })
                        if ("pokemonItem" in features) add(R.string.tyn_cheat_equipment to { editPokemonItem(pokemon) })
                        if ("pokemonStats" in features) add(R.string.tyn_cheat_pokemon_stats to { editPokemonStats(pokemon) })
                    }
                    pokemonActions.chunked(2).forEach { actionRow ->
                        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                        actionRow.forEachIndexed { index, (label, action) ->
                            row.addView(button(label, onClick = action),
                                LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(5) })
                        }
                        if (actionRow.size == 1) row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5) })
                        block.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                    }
                }
                list.addView(block, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
        }
    }

    private fun editPokemon(pokemon: TyranorCheatController.PokemonEntry) {
        val fields = listOf(
            "level" to R.string.tyn_cheat_level,
            "hp" to R.string.tyn_cheat_current_hp,
            "exp" to R.string.tyn_cheat_exp
        )
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.tyn_cheat_pokemon_edit_title, pokemon.name))
            .setItems(fields.map { activity.getString(it.second) }.toTypedArray()) { _, index ->
                val (field, label) = fields[index]
                val initial = when (field) {
                    "level" -> pokemon.level
                    "hp" -> pokemon.hp
                    else -> pokemon.exp
                }
                promptNumber(activity.getString(label), initial.toString()) { value ->
                    manager.setPokemonValue(pokemon.index, field, value) { result -> activity.runOnUiThread {
                        toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                        if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                    } }
                }
            }.show()
    }

    private fun editPokemonMoves(pokemon: TyranorCheatController.PokemonEntry) {
        manager.listPokemonMoves(pokemon.index) { moves -> activity.runOnUiThread movesUi@{
            if (panel == null || selectedTab != Tab.CHARACTER) return@movesUi
            if (moves.isEmpty()) {
                toast(activity.getString(R.string.tyn_cheat_collection_empty))
                return@movesUi
            }
            val labels = moves.map { move ->
                val current = move.name.ifEmpty { activity.getString(R.string.tyn_cheat_pokemon_move_empty) }
                activity.getString(R.string.tyn_cheat_pokemon_move_slot, move.slot + 1, current)
            }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.tyn_cheat_pokemon_move_title, pokemon.name))
                .setItems(labels) { _, selected ->
                    val move = moves[selected]
                    AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.tyn_cheat_pokemon_move_slot_title, move.slot + 1))
                        .setItems(arrayOf(
                            activity.getString(R.string.tyn_cheat_pokemon_move_replace),
                            activity.getString(R.string.tyn_cheat_pokemon_move_clear)
                        )) { _, action ->
                            if (action == 0) {
                                manager.listPokemonMoveCandidates { candidates -> activity.runOnUiThread candidatesUi@{
                                    if (panel == null || selectedTab != Tab.CHARACTER) return@candidatesUi
                                    if (candidates.isEmpty()) {
                                        toast(activity.getString(R.string.tyn_cheat_collection_empty))
                                        return@candidatesUi
                                    }
                                    showSearchablePicker(
                                        activity.getString(R.string.tyn_cheat_pokemon_move_title, pokemon.name),
                                        candidates.map { it.key to it.name }
                                    ) { key ->
                                        manager.setPokemonMove(pokemon.index, move.slot, key) { result -> activity.runOnUiThread moveResultUi@{
                                            toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                                            if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                                        } }
                                    }
                                } }
                            } else {
                                manager.setPokemonMove(pokemon.index, move.slot, "") { result -> activity.runOnUiThread clearMoveUi@{
                                    toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                                    if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                                } }
                            }
                        }.show()
                }.show()
        } }
    }

    private fun editPokemonStatus(pokemon: TyranorCheatController.PokemonEntry) {
        manager.listPokemonStatuses(pokemon.index) { statuses -> activity.runOnUiThread statusesUi@{
            if (panel == null || selectedTab != Tab.CHARACTER) return@statusesUi
            val labels = mutableListOf(activity.getString(R.string.tyn_cheat_pokemon_status_clear))
            labels.addAll(statuses.map { status ->
                val label = activity.getString(pokemonStatusLabel(status.key))
                "${if (status.active) "✓ " else ""}$label"
            })
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.tyn_cheat_pokemon_status_title, pokemon.name))
                .setItems(labels.toTypedArray()) { _, selected ->
                    val status = statuses.getOrNull(selected - 1)?.key.orEmpty()
                    manager.setPokemonStatus(pokemon.index, status) { result -> activity.runOnUiThread statusResultUi@{
                        toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                        if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                    } }
                }.show()
        } }
    }

    private fun pokemonStatusLabel(key: String): Int = when (key.uppercase()) {
        "SLEEP" -> R.string.tyn_cheat_pokemon_status_sleep
        "POISON" -> R.string.tyn_cheat_pokemon_status_poison
        "BURN" -> R.string.tyn_cheat_pokemon_status_burn
        "PARALYSIS" -> R.string.tyn_cheat_pokemon_status_paralysis
        "FROZEN" -> R.string.tyn_cheat_pokemon_status_frozen
        else -> R.string.tyn_cheat_pokemon_status_other
    }

    private fun editPokemonItem(pokemon: TyranorCheatController.PokemonEntry) {
        manager.listPickables { pickables -> activity.runOnUiThread itemsUi@{
            if (panel == null || selectedTab != Tab.CHARACTER) return@itemsUi
            val choices = buildList {
                add("" to activity.getString(R.string.tyn_cheat_pokemon_item_clear))
                pickables.asSequence().filter { it.kind == "item" }.forEach { add(it.key to it.name) }
            }
            if (choices.size <= 1) {
                toast(activity.getString(R.string.tyn_cheat_collection_empty))
                return@itemsUi
            }
             showSearchablePicker(activity.getString(R.string.tyn_cheat_pokemon_item_title, pokemon.name), choices) { key ->
                manager.setPokemonItem(pokemon.index, key) { result -> activity.runOnUiThread itemResultUi@{
                    toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                    if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                } }
            }
        } }
    }

    private fun editPokemonStats(pokemon: TyranorCheatController.PokemonEntry) {
        manager.listPokemonStats(pokemon.index) { stats -> activity.runOnUiThread statsUi@{
            if (panel == null || selectedTab != Tab.CHARACTER) return@statsUi
            val statLabels = listOf(
                R.string.tyn_cheat_stat_hp, R.string.tyn_cheat_attack, R.string.tyn_cheat_defense,
                R.string.tyn_cheat_magic_attack, R.string.tyn_cheat_magic_defense, R.string.tyn_cheat_pokemon_stat_speed
            ).map { activity.getString(it) }
            val fields = mutableListOf<Triple<String, String, Int>>()
            statLabels.forEachIndexed { index, name ->
                fields.add(Triple("iv_$index", activity.getString(R.string.tyn_cheat_pokemon_iv_format, name), stats.ivs[index]))
            }
            statLabels.forEachIndexed { index, name ->
                fields.add(Triple("ev_$index", activity.getString(R.string.tyn_cheat_pokemon_ev_format, name), stats.evs[index]))
            }
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.tyn_cheat_pokemon_stats_title, pokemon.name))
                .setItems(fields.map { it.second }.toTypedArray()) { _, selected ->
                    val (field, label, value) = fields[selected]
                    promptNumber(label, value.toString()) { updated ->
                        manager.setPokemonValue(pokemon.index, field, updated) { result -> activity.runOnUiThread statResultUi@{
                            toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                            if (result.isNotEmpty() && selectedTab == Tab.CHARACTER) renderTab()
                        } }
                    }
                }.show()
        } }
    }

    private fun showSearchablePicker(title: String, choices: List<Pair<String, String>>, onSelected: (String) -> Unit) {
        val search = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_pokemon_search)
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            textSize = 14f
            isSingleLine = true
            background = GradientDrawable().apply {
                setColor(inputColor)
                cornerRadius = 9f * activity.resources.displayMetrics.density
                setStroke(dp(1), if (darkMode) 0xFF4A554F.toInt() else 0xFFCBD5CF.toInt())
            }
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(44)
        }
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(search, LinearLayout.LayoutParams(-1, -2))
            addView(ScrollView(activity).apply {
                isFillViewport = false
                addView(results)
            }, LinearLayout.LayoutParams(-1, dp(360)).apply { topMargin = dp(8) })
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(root)
            .setNegativeButton(R.string.tyn_cheat_cancel, null)
            .create()
        fun renderChoices() {
            results.removeAllViews()
            val query = search.text.toString().trim()
            val visible = choices.asSequence()
                .filter { query.isEmpty() || it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true) }
                .take(150)
            visible.forEach { (key, name) ->
                results.addView(TextView(activity).apply {
                    text = if (key.isEmpty()) name else "$name  #$key"
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = rounded(rowColor, 8f * activity.resources.displayMetrics.density)
                    isClickable = true
                    setOnClickListener {
                        dialog.dismiss()
                        onSelected(key)
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = renderChoices()
        })
        renderChoices()
        dialog.show()
    }

    private fun editActor(actor: TyranorCheatController.ActorEntry) {
        val fields = listOf(
            "level" to R.string.tyn_cheat_level,
            "hp" to R.string.tyn_cheat_current_hp,
            "mhp" to R.string.tyn_cheat_max_hp,
            "mp" to if (capabilities?.engine == "xp") R.string.tyn_cheat_current_sp else R.string.tyn_cheat_current_mp,
            "mmp" to if (capabilities?.engine == "xp") R.string.tyn_cheat_max_sp else R.string.tyn_cheat_max_mp,
            "atk" to R.string.tyn_cheat_attack,
            "def" to R.string.tyn_cheat_defense,
            "mat" to R.string.tyn_cheat_magic_attack,
            "mdf" to R.string.tyn_cheat_magic_defense,
            "agi" to R.string.tyn_cheat_agility,
            "luk" to R.string.tyn_cheat_luck,
            "dex" to R.string.tyn_cheat_dexterity
        ).filter { capabilities?.actorValues?.contains(it.first) == true }
        if (fields.isEmpty()) return
        val labels = fields.map { activity.getString(it.second) }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.tyn_cheat_actor_edit_title, actor.name))
            .setItems(labels) { _, index ->
                val (field, label) = fields[index]
                val initial = when (field) {
                    "level" -> actor.level
                    "hp" -> actor.hp
                    "mhp" -> actor.maxHp
                    "mp" -> actor.mp
                    "mmp" -> actor.maxMp
                    else -> 0
                }
                promptNumber(activity.getString(label), initial.toString()) { value ->
                    manager.setActorValue(actor.id, field, value) { result -> activity.runOnUiThread {
                        toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                    } }
                }
            }.show()
    }

    private fun editActorCollection(actorId: Int, collection: String) {
        manager.listActorCollection(actorId, collection) { entries ->
            if (panel == null || selectedTab != Tab.CHARACTER) return@listActorCollection
            if (entries.isEmpty()) {
                toast(activity.getString(R.string.tyn_cheat_collection_empty))
                return@listActorCollection
            }
            val choices = entries.map { entry ->
                entry.id.toString() to "${if (entry.enabled) "✓ " else "○ "}${entry.name}"
            }
            showSearchablePicker(
                activity.getString(if (collection == "skills") R.string.tyn_cheat_skills else R.string.tyn_cheat_states),
                choices
            ) { selectedId ->
                val entry = entries.firstOrNull { it.id.toString() == selectedId } ?: return@showSearchablePicker
                manager.setActorCollection(actorId, collection, entry.id, !entry.enabled) { result ->
                    activity.runOnUiThread {
                        if (result.isNotEmpty()) toast(result) else toast(activity.getString(R.string.tyn_cheat_applied))
                    }
                }
            }
        }
    }

    private fun editActorEquips(actorId: Int) {
        manager.listActorEquips(actorId) { slots ->
            if (panel == null || selectedTab != Tab.CHARACTER) return@listActorEquips
            if (slots.isEmpty()) {
                toast(activity.getString(R.string.tyn_cheat_equipment_empty))
                return@listActorEquips
            }
            val labels = slots.map { "${it.slotName}: ${it.itemName}" }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(R.string.tyn_cheat_equipment)
                .setItems(labels) { _, index ->
                    val slot = slots[index]
                    manager.listEquipCandidates(actorId, slot.index) { candidates ->
                        if (panel == null || selectedTab != Tab.CHARACTER) return@listEquipCandidates
                        if (candidates.isEmpty()) {
                            toast(activity.getString(R.string.tyn_cheat_equipment_candidates_empty))
                            return@listEquipCandidates
                        }
                        val choices = candidates.map { "${it.name}  #${it.itemId}" }.toTypedArray()
                        AlertDialog.Builder(activity)
                            .setTitle(activity.getString(R.string.tyn_cheat_equipment_select, slot.slotName))
                            .setItems(choices) { _, candidateIndex ->
                                val candidate = candidates[candidateIndex]
                                manager.setActorEquip(actorId, slot.index, candidate.kind, candidate.itemId) { result -> activity.runOnUiThread {
                                    toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                                } }
                            }.show()
                    }
                }.show()
        }
    }

    private fun editItemQuantity(item: TyranorCheatController.Pickable) {
        promptNumber(activity.getString(R.string.tyn_cheat_quantity_title, item.name), item.quantity.toString()) { value ->
            val quantity = value.toIntOrNull()
            if (quantity == null || quantity < 0) {
                toast(activity.getString(R.string.tyn_cheat_quantity_invalid))
            } else {
                manager.setItemQuantity(item.kind, item.key, quantity) { result -> activity.runOnUiThread {
                    toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                    if (selectedTab == Tab.ITEMS) renderTab()
                } }
            }
        }
    }

    private fun promptNumber(title: String, initial: String, onApply: (String) -> Unit) {
        val input = EditText(activity).apply {
            setText(initial)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            background = rounded(inputColor, 9f * activity.resources.displayMetrics.density)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(R.string.tyn_cheat_cancel, null)
            .setPositiveButton(R.string.tyn_cheat_apply) { _, _ -> onApply(input.text.toString().trim()) }
            .show()
    }

    private fun renderMiniMap() {
        section(R.string.tyn_cheat_minimap_title)
        val viewport = ScrollView(activity).apply {
            isFillViewport = false
            addView(HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.tyn_cheat_minimap_loading)
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    tag = "minimap-content"
                })
            })
        }
        currentCard?.addView(viewport, LinearLayout.LayoutParams(-1, dp(210)))
        currentCard?.addView(TextView(activity).apply {
            text = activity.getString(R.string.tyn_cheat_minimap_legend)
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(4), dp(5), dp(4), 0)
        })
        manager.loadMiniMap { map ->
            if (panel == null || selectedTab != Tab.MAP) return@loadMiniMap
            val horizontal = (viewport.getChildAt(0) as? HorizontalScrollView) ?: return@loadMiniMap
            horizontal.removeAllViews()
            if (map == null || map.rows.isEmpty()) {
                horizontal.addView(TextView(activity).apply {
                    text = activity.getString(R.string.tyn_cheat_minimap_unavailable)
                    setTextColor(secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                })
                return@loadMiniMap
            }
            val cell = dp(5).coerceAtLeast(3)
            val mapView = object : View(activity) {
                private val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
                private val columns = map.rows.fold(0) { widest, row -> maxOf(widest, row.length) }

                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val desiredWidth = columns * cell
                    val desiredHeight = map.rows.size * cell
                    setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    map.rows.forEachIndexed { y, row ->
                        row.forEachIndexed { x, tile ->
                            paint.color = when (tile) {
                                '@' -> 0xFF33C481.toInt()
                                'E' -> 0xFFF7F8F7.toInt()
                                '.' -> 0xFFBFDCC7.toInt()
                                else -> 0xFF39413D.toInt()
                            }
                            val left = x * cell.toFloat()
                            val top = y * cell.toFloat()
                            canvas.drawRect(left, top, left + cell, top + cell, paint)
                        }
                    }
                    map.events.forEach { event ->
                        val left = event.x * cell.toFloat()
                        val top = event.y * cell.toFloat()
                        paint.style = Paint.Style.FILL
                        paint.color = Color.WHITE
                        canvas.drawRect(left, top, left + cell, top + cell, paint)
                        if (cell >= dp(9)) {
                            paint.color = 0xFF202522.toInt()
                            paint.textAlign = Paint.Align.CENTER
                            paint.textSize = cell * 0.72f
                            paint.typeface = Typeface.DEFAULT_BOLD
                            canvas.drawText(event.id.toString(), left + cell / 2f, top + cell * 0.78f, paint)
                        }
                    }
                    if (map.playerX in 0 until map.width && map.playerY in 0 until map.height) {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.5f
                        paint.color = 0xFF33C481.toInt()
                        val left = map.playerX * cell.toFloat()
                        val top = map.playerY * cell.toFloat()
                        canvas.drawRect(left + 1f, top + 1f, left + cell - 1f, top + cell - 1f, paint)
                        paint.style = Paint.Style.FILL
                    }
                }
            }.apply {
                isClickable = true
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_UP -> {
                            val x = (event.x / cell).toInt()
                            val y = (event.y / cell).toInt()
                            map.events.firstOrNull { it.x == x && it.y == y }?.let { marker ->
                                minimapEventSelected?.invoke(marker.id)
                            }
                            true
                        }
                        else -> true
                    }
                }
            }
            horizontal.addView(mapView)
        }
    }

    private fun renderEvents() {
        section(R.string.tyn_cheat_events_title)
        val search = searchField(R.string.tyn_cheat_event_search)
        currentCard?.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        list.addView(noteView(R.string.tyn_cheat_events_loading))
        var entries = emptyList<TyranorCheatController.EventEntry>()
        var loaded = false
        var renderedRows = emptyMap<Int, View>()
        fun render() {
            list.removeAllViews()
            renderedRows = emptyMap()
            if (!loaded) {
                list.addView(noteView(R.string.tyn_cheat_events_loading))
                return
            }
            val query = search.text.toString().trim()
            val events = entries.filter { query.isEmpty() || it.name.contains(query, true) || it.id.toString().contains(query) }
            if (events.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_events_empty))
                return
            }
            val currentRows = mutableMapOf<Int, View>()
            events.forEach { event ->
                val block = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val selectedColor = if (darkMode) 0xFF263D31.toInt() else 0xFFE1F2E8.toInt()
                    background = rounded(if (selectedMapEventId == event.id) selectedColor else rowColor,
                        10f * activity.resources.displayMetrics.density)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnClickListener {
                        selectedMapEventId = event.id
                        render()
                    }
                }
                block.addView(TextView(activity).apply {
                    text = activity.getString(R.string.tyn_cheat_event_summary, event.id, event.name, event.x, event.y,
                        event.pageCount, event.firstPageCommandCount)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
                block.addView(button(R.string.tyn_cheat_event_teleport) {
                    manager.warpToPosition(event.mapId, event.x, event.y) { result -> activity.runOnUiThread {
                        toast(if (result == "ok") activity.getString(R.string.tyn_cheat_map_warped) else result)
                    } }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                if (capabilities?.features?.contains("eventSelfSwitches") == true) {
                    val switches = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    listOf("A", "B", "C", "D").forEach { key ->
                        switches.addView(CheckBox(activity).apply {
                            text = key
                            setTextColor(primaryTextColor)
                            buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                            isChecked = event.selfSwitches[key] == true
                            setOnCheckedChangeListener { button, enabled ->
                                button.isEnabled = false
                                manager.setEventSelfSwitch(event.id, key, enabled) { result -> activity.runOnUiThread {
                                    button.isEnabled = true
                                    if (result.isNotEmpty()) { button.isChecked = !enabled; toast(result) }
                                    else manager.listEvents { refreshed ->
                                        if (panel != null && selectedTab == Tab.MAP) { entries = refreshed; render() }
                                    }
                                } }
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    block.addView(switches)
                    val allSwitches = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    listOf(R.string.tyn_cheat_event_switches_all_on to true, R.string.tyn_cheat_event_switches_all_off to false).forEach { (label, enabled) ->
                        allSwitches.addView(button(label) {
                            manager.setEventSelfSwitches(event.id, enabled) { result -> activity.runOnUiThread {
                                if (result.isNotEmpty()) toast(result)
                                else manager.listEvents { refreshed ->
                                    if (panel != null && selectedTab == Tab.MAP) { entries = refreshed; render() }
                                }
                            } }
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
                    }
                    block.addView(allSwitches, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                }
                list.addView(block, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                currentRows[event.id] = block
            }
            renderedRows = currentRows
        }
        minimapEventSelected = { eventId ->
            selectedMapEventId = eventId
            if (search.text.isNotEmpty()) search.setText("") else render()
            toast(activity.getString(R.string.tyn_cheat_event_selected, eventId))
            val target = renderedRows[eventId]
            target?.let { row ->
                bodyScroll?.post {
                    val scroll = bodyScroll ?: return@post
                    val rect = Rect()
                    scroll.offsetDescendantRectToMyCoords(row, rect)
                    scroll.smoothScrollTo(0, rect.top)
                }
            }
        }
        search.addTextChangedListener(simpleTextWatcher { render() })
        manager.listEvents { result ->
            if (panel == null || selectedTab != Tab.MAP) return@listEvents
            entries = result
            loaded = true
            render()
        }
    }

    private fun renderBattle() {
        section(R.string.tyn_cheat_section_battle)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (capabilities?.features?.contains("battle") != true) {
            list.addView(noteView(R.string.tyn_cheat_battle_idle))
            return
        }
        manager.listBattleEnemies { enemies ->
            if (panel == null || selectedTab != Tab.BATTLE) return@listBattleEnemies
            if (enemies.isEmpty()) {
                list.removeAllViews()
                list.addView(noteView(R.string.tyn_cheat_battle_idle))
                return@listBattleEnemies
            }
            manager.listBattleActors { actors ->
                if (panel == null || selectedTab != Tab.BATTLE) return@listBattleActors
                list.removeAllViews()
                enemies.forEach { enemy ->
                    val itemRow = row()
                    itemRow.addView(TextView(activity).apply {
                        text = activity.getString(R.string.tyn_cheat_enemy_summary, enemy.name, enemy.hp, enemy.maxHp)
                        setTextColor(primaryTextColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    itemRow.addView(button(R.string.tyn_cheat_set) {
                        promptNumber(activity.getString(R.string.tyn_cheat_enemy_hp_title, enemy.name), enemy.hp.toString()) { value ->
                            val hp = value.toIntOrNull()
                            if (hp == null || hp < 0) toast(activity.getString(R.string.tyn_cheat_quantity_invalid))
                            else manager.setEnemyHp(enemy.index, hp) { result -> activity.runOnUiThread {
                                if (result.isNotEmpty()) toast(result) else if (selectedTab == Tab.BATTLE) renderTab()
                            } }
                        }
                    })
                    list.addView(itemRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                }
                if (actors.isNotEmpty()) {
                    list.addView(TextView(activity).apply {
                        text = activity.getString(R.string.tyn_cheat_battle_allies)
                        setTextColor(primaryTextColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(4), dp(10), dp(4), dp(4))
                    })
                    actors.forEach { actor ->
                        list.addView(TextView(activity).apply {
                            text = activity.getString(R.string.tyn_cheat_battle_ally_summary, actor.name, actor.hp, actor.maxHp)
                            setTextColor(secondaryTextColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setPadding(dp(8), dp(6), dp(8), dp(6))
                        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                    }
                }
                section(R.string.tyn_cheat_battle_controls)
                toggle(TyranorCheatToggle.ONE_HIT_KILL)
                toggle(TyranorCheatToggle.INVINCIBILITY)
                toggle(TyranorCheatToggle.ALWAYS_CRIT)
                toggle(TyranorCheatToggle.ESCAPE_ALWAYS)
                toggle(TyranorCheatToggle.FREE_SKILL_COST)
                action(R.string.tyn_cheat_battle_kill_enemies, "killEnemies")
                action(R.string.tyn_cheat_instant_win, "forceVictory")
                if (capabilities?.features?.contains("battleEscape") == true) {
                    action(R.string.tyn_cheat_force_escape, "forceEscape")
                }
            }
        }
    }

    private fun renderSaveSlots() {
        section(R.string.tyn_cheat_save_slots)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        list.addView(noteView(R.string.tyn_cheat_save_slots_loading))
        manager.listSaveSlots { slots ->
            if (panel == null || selectedTab != Tab.SAVE) return@listSaveSlots
            list.removeAllViews()
            if (slots.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_save_slots_empty))
                return@listSaveSlots
            }
            slots.forEach { slot ->
                val block = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(rowColor, 10f * activity.resources.displayMetrics.density)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val title = when {
                    slot.isCurrent -> activity.getString(R.string.tyn_cheat_save_slot_current, slot.id)
                    slot.exists -> activity.getString(R.string.tyn_cheat_save_slot_exists, slot.id, slot.label)
                    else -> activity.getString(R.string.tyn_cheat_save_slot_empty, slot.id)
                }
                block.addView(TextView(activity).apply {
                    text = title
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
                val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                actions.addView(button(R.string.tyn_cheat_save_slot_action) {
                    manager.runAction("saveSlot:${slot.id}") { result -> activity.runOnUiThread {
                        if (result.startsWith("Save slot") && result.endsWith("saved")) {
                            toast(activity.getString(R.string.tyn_cheat_save_completed))
                            if (selectedTab == Tab.SAVE) renderTab()
                        } else if (result.isNotEmpty() && result != "ok") toast(result)
                    } }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (slot.exists) {
                    actions.addView(button(R.string.tyn_cheat_load_slot) {
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.tyn_cheat_load_confirm_title)
                            .setMessage(R.string.tyn_cheat_load_confirm_message)
                            .setNegativeButton(R.string.tyn_cheat_cancel, null)
                            .setPositiveButton(R.string.tyn_cheat_continue) { _, _ ->
                                manager.runAction("loadSlot:${slot.id}") { result -> activity.runOnUiThread {
                                    if (result.startsWith("Save slot") && result.endsWith("loaded")) toast(activity.getString(R.string.tyn_cheat_load_completed))
                                    else if (result.isNotEmpty() && result != "ok") toast(result)
                                } }
                            }.show()
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                    actions.addView(button(R.string.tyn_cheat_delete_slot) {
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.tyn_cheat_delete_slot_title)
                            .setMessage(activity.getString(R.string.tyn_cheat_delete_slot_message, slot.id))
                            .setNegativeButton(R.string.tyn_cheat_cancel, null)
                            .setPositiveButton(R.string.tyn_cheat_delete_slot) { _, _ ->
                                manager.deleteSaveSlot(slot.id) { result -> activity.runOnUiThread {
                                    if (result.startsWith("Save slot")) {
                                        toast(activity.getString(R.string.tyn_cheat_save_deleted))
                                        if (selectedTab == Tab.SAVE) renderTab()
                                    } else toast(result)
                                } }
                            }.show()
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
                }
                block.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                list.addView(block, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
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
        if (capabilities?.toggles?.contains(toggle.wireName) != true) return
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
        if (capabilities?.values?.contains(field) != true) return
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
                    if (field == "gold") refreshCurrentGold()
                }
            }
        }, LinearLayout.LayoutParams(dp(78), dp(42)).apply { marginStart = dp(8) })
        row.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        addRow(row)
    }

    private fun showCurrentGold() {
        val view = TextView(activity).apply {
            text = activity.getString(R.string.tyn_cheat_current_gold_loading)
            setTextColor(secondaryTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(4), dp(4), dp(4), dp(2))
        }
        currentGoldView = view
        addRow(view)
        refreshCurrentGold()
    }

    private fun refreshCurrentGold() {
        val target = currentGoldView ?: return
        manager.getGold { amount -> activity.runOnUiThread {
            if (panel == null || currentGoldView !== target || selectedTab != Tab.QUICK) return@runOnUiThread
            target.text = if (amount == null) activity.getString(R.string.tyn_cheat_current_gold_unavailable)
                else activity.getString(R.string.tyn_cheat_current_gold_value, amount)
        } }
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
            } else if (name == "__refresh") {
                renderTab()
            } else if (name == "__resetPad") {
                onResetPad()
                toast(activity.getString(R.string.tyn_cheat_pad_reset))
            } else if (name == "__exportDiagnostics") {
                dismiss()
                onExportDiagnostics()
            } else if (name == "loadScreen") {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.tyn_cheat_load_confirm_title)
                    .setMessage(R.string.tyn_cheat_load_confirm_message)
                    .setNegativeButton(R.string.tyn_cheat_cancel, null)
                    .setPositiveButton(R.string.tyn_cheat_continue) { _, _ -> runNativeScreenAction(name) }
                    .show()
            } else if (name == "saveScreen") {
                runNativeScreenAction(name)
            } else {
                manager.runAction(name) { result -> activity.runOnUiThread {
                    toast(result.ifEmpty { activity.getString(R.string.tyn_cheat_applied) })
                    if (name == "maxGold") refreshCurrentGold()
                } }
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
        TyranorCheatToggle.EVENT_SPEED -> R.string.tyn_cheat_event_speed
        TyranorCheatToggle.MESSAGE_SKIP -> R.string.tyn_cheat_message_skip
        TyranorCheatToggle.ONE_HIT_KILL -> R.string.tyn_cheat_one_hit
        TyranorCheatToggle.INVINCIBILITY -> R.string.tyn_cheat_invincibility
        TyranorCheatToggle.ALWAYS_CRIT -> R.string.tyn_cheat_always_crit
        TyranorCheatToggle.ESCAPE_ALWAYS -> R.string.tyn_cheat_escape_always
        TyranorCheatToggle.FREE_SKILL_COST -> R.string.tyn_cheat_free_skill
    })

    private fun requestCapabilities() {
        if (panel == null || capabilitiesLoaded) return
        capabilityAttempts++
        manager.loadCapabilities { result ->
            if (panel == null) return@loadCapabilities
            if (result.engine == "unknown" && capabilityAttempts < 12) {
                activity.window.decorView.postDelayed({ requestCapabilities() }, 500)
            } else {
                capabilities = result
                capabilitiesLoaded = true
                renderTab()
            }
        }
    }

    private fun runNativeScreenAction(name: String) {
        dismiss()
        manager.runAction(name) { result ->
            if (result.isNotEmpty() && result != "ok") activity.runOnUiThread { toast(result) }
        }
    }

    private fun renderSystem() {
        section(R.string.tyn_cheat_section_system)
        val search = searchField(R.string.tyn_cheat_system_search)
        currentCard?.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        note(R.string.tyn_cheat_system_limit_note)
        var filter = "all"
        val filters = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentCard?.addView(filters, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        var entries = emptyList<TyranorCheatController.SystemEntry>()
        var loaded = false
        fun render() {
            list.removeAllViews()
            if (!loaded) {
                list.addView(noteView(R.string.tyn_cheat_system_loading))
                return
            }
            val query = search.text.toString().trim()
            val matching = entries.filter { entry ->
                (filter == "all" || entry.kind == filter) &&
                    (query.isEmpty() || entry.name.contains(query, true) || entry.id.toString().contains(query) || entry.value.contains(query, true))
            }
            val visible = if (query.isEmpty() && filter == "all") {
                (matching.filter { it.kind == "switch" }.take(200) + matching.filter { it.kind == "variable" }.take(200))
            } else matching.take(200)
            if (visible.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_system_empty))
                return
            }
            visible.forEach { entry ->
                val itemRow = row()
                val title = when (entry.kind) {
                    "switch" -> activity.getString(R.string.tyn_cheat_system_switch_format, entry.id, entry.name)
                    else -> activity.getString(R.string.tyn_cheat_system_variable_format, entry.id, entry.name)
                }
                itemRow.addView(TextView(activity).apply {
                    text = "$title\n${activity.getString(R.string.tyn_cheat_system_value_format, entry.value)}"
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 3
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (entry.kind == "switch") {
                    val check = CheckBox(activity).apply {
                        buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                        isChecked = entry.value == "true"
                        setOnCheckedChangeListener { button, enabled ->
                            button.isEnabled = false
                            manager.setSystemSwitch(entry.id, enabled) { result ->
                                activity.runOnUiThread {
                                    if (result.isNotEmpty()) toast(result) else loadSystemAgain { entries = it; render() }
                                    button.isEnabled = true
                                    if (result.isNotEmpty()) button.isChecked = !enabled
                                }
                            }
                        }
                    }
                    itemRow.addView(check)
                } else {
                    itemRow.addView(button(R.string.tyn_cheat_set) {
                        editSystemVariable(entry) { loadSystemAgain { entries = it; render() } }
                    })
                }
                list.addView(itemRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
        }
        fun rebuildFilters() {
            filters.removeAllViews()
            listOf("all" to R.string.tyn_cheat_filter_all, "switch" to R.string.tyn_cheat_filter_switches,
                "variable" to R.string.tyn_cheat_filter_variables).forEach { (key, label) ->
                filters.addView(button(label, primary = filter == key) { filter = key; rebuildFilters(); render() },
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            }
        }
        rebuildFilters()
        search.addTextChangedListener(simpleTextWatcher { render() })
        render()
        manager.listSystem { result ->
            if (panel == null || selectedTab != Tab.SYSTEM) return@listSystem
            entries = result
            loaded = true
            render()
        }
    }

    private fun renderMap() {
        section(R.string.tyn_cheat_map_list)
        val search = searchField(R.string.tyn_cheat_map_search)
        currentCard?.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        note(R.string.tyn_cheat_map_limit_note)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        currentCard?.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        var entries = emptyList<TyranorCheatController.MapEntry>()
        var loaded = false
        fun render() {
            list.removeAllViews()
            if (!loaded) {
                list.addView(noteView(R.string.tyn_cheat_map_loading))
                return
            }
            val query = search.text.toString().trim()
            val visible = entries.filter { query.isEmpty() || it.name.contains(query, true) || it.id.toString().contains(query) }.take(300)
            if (visible.isEmpty()) {
                list.addView(noteView(R.string.tyn_cheat_map_empty))
                return
            }
            visible.forEach { entry ->
                val itemRow = row()
                itemRow.addView(TextView(activity).apply {
                    val mapName = "  ".repeat(entry.depth.coerceIn(0, 8)) + entry.name
                    text = if (entry.isCurrent) {
                        val title = activity.getString(R.string.tyn_cheat_map_current_format, entry.id, mapName)
                        if (entry.currentX != null && entry.currentY != null && entry.currentDirection != null) {
                            "$title\n${activity.getString(R.string.tyn_cheat_map_position_format, entry.currentX, entry.currentY, entry.currentDirection)}"
                        } else title
                    } else activity.getString(R.string.tyn_cheat_map_entry_format, entry.id, mapName)
                    setTextColor(primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val warp = button(R.string.tyn_cheat_map_warp) {
                    manager.warpToMap(entry.id) { result -> activity.runOnUiThread {
                        toast(if (result == "ok") activity.getString(R.string.tyn_cheat_map_warped) else result)
                        if (result == "ok") loadMapsAgain { entries = it; render() }
                    } }
                }
                warp.isEnabled = !entry.isCurrent
                itemRow.addView(warp)
                itemRow.addView(button(R.string.tyn_cheat_map_warp_coordinates) { promptCoordinates(entry) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(5) })
                list.addView(itemRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
        }
        search.addTextChangedListener(simpleTextWatcher { render() })
        render()
        manager.listMaps { result ->
            if (panel == null || selectedTab != Tab.MAP) return@listMaps
            entries = result
            loaded = true
            render()
        }
    }

    private fun loadSystemAgain(onLoaded: (List<TyranorCheatController.SystemEntry>) -> Unit) {
        manager.listSystem { if (panel != null && selectedTab == Tab.SYSTEM) onLoaded(it) }
    }

    private fun loadMapsAgain(onLoaded: (List<TyranorCheatController.MapEntry>) -> Unit) {
        manager.listMaps { if (panel != null && selectedTab == Tab.MAP) onLoaded(it) }
    }

    private fun promptCoordinates(entry: TyranorCheatController.MapEntry) {
        val mapId = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_map_id)
            setText(entry.id.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(primaryTextColor)
        }
        val x = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_map_x)
            entry.currentX?.let { setText(it.toString()) }
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(primaryTextColor)
        }
        val y = EditText(activity).apply {
            hint = activity.getString(R.string.tyn_cheat_map_y)
            entry.currentY?.let { setText(it.toString()) }
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(primaryTextColor)
        }
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
            addView(mapId)
            addView(x)
            addView(y)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.tyn_cheat_map_coordinates_title)
            .setView(fields)
            .setNegativeButton(R.string.tyn_cheat_cancel, null)
            .setPositiveButton(R.string.tyn_cheat_map_warp) { _, _ ->
                val id = mapId.text.toString().toIntOrNull()
                val tx = x.text.toString().toIntOrNull()
                val ty = y.text.toString().toIntOrNull()
                if (id == null || tx == null || ty == null || id <= 0 || tx < 0 || ty < 0) {
                    toast(activity.getString(R.string.tyn_cheat_map_coordinates_invalid))
                } else manager.warpToPosition(id, tx, ty) { result -> activity.runOnUiThread {
                    toast(if (result == "ok") activity.getString(R.string.tyn_cheat_map_warped) else result)
                    if (result == "ok") dismiss()
                } }
            }.show()
    }

    private fun editSystemVariable(entry: TyranorCheatController.SystemEntry, onSaved: () -> Unit) {
        val input = EditText(activity).apply {
            setText(if (entry.valueType == "complex") "" else entry.value)
            setSelection(text.length)
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            hint = activity.getString(R.string.tyn_cheat_json_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = GradientDrawable().apply {
                setColor(inputColor)
                cornerRadius = 9f * activity.resources.displayMetrics.density
                setStroke(dp(1), if (darkMode) 0xFF4A554F.toInt() else 0xFFCBD5CF.toInt())
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.tyn_cheat_system_edit_title, entry.id, entry.name))
            .setView(input)
            .setNegativeButton(R.string.tyn_cheat_cancel, null)
            .setPositiveButton(R.string.tyn_cheat_apply, null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        manager.setSystemVariable(entry.id, input.text.toString()) { result -> activity.runOnUiThread {
                            if (result.isEmpty()) {
                                dialog.dismiss()
                                onSaved()
                                toast(activity.getString(R.string.tyn_cheat_applied))
                            } else toast(result)
                        } }
                    }
                }
            }.show()
    }

    private fun searchField(hintRes: Int): EditText = EditText(activity).apply {
        hint = activity.getString(hintRes)
        setTextColor(primaryTextColor)
        setHintTextColor(secondaryTextColor)
        textSize = 14f
        isSingleLine = true
        background = GradientDrawable().apply {
            setColor(inputColor)
            cornerRadius = 9f * activity.resources.displayMetrics.density
            setStroke(dp(1), if (darkMode) 0xFF4A554F.toInt() else 0xFFCBD5CF.toInt())
        }
        setPadding(dp(12), 0, dp(12), 0)
        minHeight = dp(44)
    }

    private fun simpleTextWatcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
