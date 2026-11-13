package com.tyranor.runtime.ui

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.tyranor.runtime.cheat.TyranorCheatController
import com.tyranor.runtime.cheat.TyranorCheatToggle
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MVMZ cheat panel UI hosted by the RGSS module.
 *
 * The HTML/CSS interaction is the MVMZ panel. [RgssCheatApi] only translates
 * its calls to the module's own Ruby-backed [TyranorCheatController].
 */
class TyranorMvmzCheatPanel(
    private val activity: Activity,
    private val parent: FrameLayout,
    private val controller: TyranorCheatController,
    private val padAvailable: () -> Boolean,
    private val enterPadEdit: () -> Unit,
    private val resetPad: () -> Unit,
    private val keepRuntimeActionsOnTop: () -> Unit,
    private val restoreGameInputFocus: () -> Unit,
) {
    private val webView = WebView(activity)
    @Volatile private var pageLoaded = false
    @Volatile private var openRequested = false
    @Volatile private var destroyed = false

    init {
        webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            addJavascriptInterface(RgssCheatApi(activity, controller), CHEAT_BRIDGE)
            addJavascriptInterface(RgssInputApi(activity), INPUT_BRIDGE)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return uri.scheme != "file" || uri.path?.startsWith("/android_asset/") != true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (destroyed || !url.endsWith(PANEL_ASSET)) return
                    pageLoaded = true
                    if (openRequested) openPanel()
                }
            }
        }
        parent.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        webView.loadUrl("file:///android_asset/$PANEL_ASSET")
    }

    fun show() {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        openRequested = true
        webView.visibility = View.VISIBLE
        webView.bringToFront()
        keepRuntimeActionsOnTop()
        webView.requestFocus()
        if (pageLoaded) openPanel()
    }

    fun handleBack(): Boolean {
        if (!openRequested || destroyed) return false
        webView.evaluateJavascript("window.TyranorModUI && window.TyranorModUI.close();", null)
        return true
    }

    fun dismiss() {
        if (destroyed) return
        openRequested = false
        webView.clearFocus()
        webView.visibility = View.GONE
        restoreGameInputFocus()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        openRequested = false
        webView.stopLoading()
        webView.removeJavascriptInterface(CHEAT_BRIDGE)
        webView.removeJavascriptInterface(INPUT_BRIDGE)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun openPanel() {
        if (!openRequested || destroyed) return
        webView.evaluateJavascript("window.TyranorModUI && window.TyranorModUI.open();", null)
        keepRuntimeActionsOnTop()
    }

    private fun closePanel(restoreFocus: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            openRequested = false
            if (!destroyed) {
                webView.clearFocus()
                webView.visibility = View.GONE
                if (restoreFocus) restoreGameInputFocus()
            }
        } else {
            activity.runOnUiThread {
                openRequested = false
                if (!destroyed) {
                    webView.clearFocus()
                    webView.visibility = View.GONE
                    if (restoreFocus) restoreGameInputFocus()
                }
            }
        }
    }

    private inner class RgssInputApi(private val host: Activity) {
        @JavascriptInterface
        fun isAvailable(): Boolean = !destroyed && !host.isFinishing && padAvailable()

        @JavascriptInterface
        fun enterEdit() {
            host.runOnUiThread {
                if (destroyed || host.isFinishing || host.isDestroyed) return@runOnUiThread
                runCatching(enterPadEdit).onFailure { Log.e(TAG, "enter RGSS pad editor failed", it) }
            }
        }

        @JavascriptInterface
        fun resetToDefaults() {
            host.runOnUiThread {
                if (destroyed || host.isFinishing || host.isDestroyed) return@runOnUiThread
                runCatching(resetPad).onFailure { Log.e(TAG, "reset RGSS pad layout failed", it) }
            }
        }
    }

    private inner class RgssCheatApi(
        private val host: Activity,
        private val cheats: TyranorCheatController,
    ) {
        @JavascriptInterface
        fun uiLanguageTag(): String = host.intent
            ?.getStringExtra("com.tyranor.runtime.extra.UI_LANGUAGE_TAG")
            ?.takeIf { it.isNotBlank() }
            ?: host.resources.configuration.locale?.toLanguageTag().orEmpty()

        @JavascriptInterface
        fun onPanelClosed(restoreFocus: Boolean) = closePanel(restoreFocus)

        /** Called only from WebView's JavaScript bridge thread, never from Android's main thread. */
        @JavascriptInterface
        fun invoke(method: String, argsJson: String): String {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return errorJson("RGSS 修改器调用不能在主线程等待")
            }
            val args = runCatching { JSONArray(argsJson) }.getOrElse { JSONArray() }
            val latch = CountDownLatch(1)
            val completed = AtomicBoolean(false)
            var result: Any? = null
            fun finish(value: Any?) {
                if (completed.compareAndSet(false, true)) {
                    result = value
                    latch.countDown()
                }
            }

            if (destroyed || host.isFinishing || host.isDestroyed) return errorJson("RGSS 宿主已关闭")
            host.runOnUiThread {
                if (destroyed || host.isFinishing || host.isDestroyed) {
                    finish(JsFailure("RGSS 宿主已关闭"))
                } else {
                    try {
                        dispatch(method, args, ::finish)
                    } catch (error: Exception) {
                        Log.e(TAG, "RGSS cheat UI call failed: $method", error)
                        finish(JsFailure(error.message ?: error.javaClass.simpleName))
                    }
                }
            }

            // Ruby commands are polled on Android's main Handler. This wait is on
            // WebView's private JS bridge thread, so game input and Ruby polling continue.
            if (!latch.await(COMMAND_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return errorJson("RGSS 游戏没有及时响应这项操作")
            }
            val value = result
            return if (value is JsFailure) errorJson(value.message)
            else JSONObject().put("value", jsonValue(value)).toString()
        }

        private fun dispatch(method: String, a: JSONArray, done: (Any?) -> Unit) {
            when (method) {
                "isReady" -> done(cheats.isReady())
                "getState" -> done(mapOf(
                    "godMode" to cheats.isEnabled(TyranorCheatToggle.INVINCIBILITY),
                    "hpmp" to cheats.isEnabled(TyranorCheatToggle.UNLIMITED_HPMP),
                    "tp" to cheats.isEnabled(TyranorCheatToggle.UNLIMITED_TP),
                    "points" to cheats.isEnabled(TyranorCheatToggle.CUSTOM_POINTS_LOCK),
                    "gold" to cheats.isEnabled(TyranorCheatToggle.UNLIMITED_GOLD),
                    "coins" to cheats.isEnabled(TyranorCheatToggle.UNLIMITED_COINS),
                    "status" to cheats.isEnabled(TyranorCheatToggle.STATUS_IMMUNITY),
                    "items" to cheats.isEnabled(TyranorCheatToggle.UNLIMITED_ITEMS),
                    "oneHit" to cheats.isEnabled(TyranorCheatToggle.ONE_HIT_KILL),
                    "alwaysCrit" to cheats.isEnabled(TyranorCheatToggle.ALWAYS_CRIT),
                    "noclip" to cheats.isEnabled(TyranorCheatToggle.WALK_THROUGH_WALLS),
                    "noEncounter" to cheats.isEnabled(TyranorCheatToggle.NO_ENCOUNTERS),
                    "speed" to cheats.isEnabled(TyranorCheatToggle.SPEED_BOOST),
                    "speedMultiplier" to cheats.speedMultiplier(),
                    "gameSpeedMultiplier" to cheats.gameSpeedMultiplier(),
                    "expMultiplier" to cheats.expMultiplier(),
                    "dropMultiplier" to cheats.dropMultiplier(),
                    "fastText" to cheats.isEnabled(TyranorCheatToggle.FAST_TEXT),
                    "eventSpeed" to cheats.isEnabled(TyranorCheatToggle.EVENT_SPEED),
                    "msgSkip" to cheats.isEnabled(TyranorCheatToggle.MESSAGE_SKIP),
                    "escape" to cheats.isEnabled(TyranorCheatToggle.ESCAPE_ALWAYS),
                    "freeSkill" to cheats.isEnabled(TyranorCheatToggle.FREE_SKILL_COST),
                ))
                "listCapabilities" -> cheats.loadCapabilities { capabilities ->
                    done(mapOf("engine" to capabilities.engine, "toggles" to capabilities.toggles, "values" to capabilities.values))
                }
                "setFlag" -> setFlag(argString(a, 0), argBoolean(a, 1), done)
                "setSpeed" -> cheats.setValue("speed", argString(a, 0)) {
                    completeMessage(it, done)
                }
                "setGameSpeed" -> cheats.setGameSpeed(argString(a, 0)) {
                    completeMessage(it, done)
                }
                "setRewardMultiplier" -> cheats.setRewardMultiplier(argString(a, 0), argString(a, 1)) {
                    completeMessage(it, done)
                }
                "getGold" -> cheats.getGold { done(it ?: 0) }
                "setGold" -> cheats.setValue("gold", argString(a, 0)) { message ->
                    completeMessage(message, done) {
                        cheats.getGold { done(it ?: 0) }
                    }
                }
                "listDatabase" -> listDatabase(a, done)
                "itemCount" -> itemCount(argString(a, 0), argString(a, 1), done)
                "setItemCount" -> cheats.setItemQuantity(
                    argString(a, 0), argString(a, 1), argInt(a, 2),
                ) { completeMessage(it, done) { done(argInt(a, 2)) } }
                "listActors" -> cheats.listActors { actors -> done(actors.map { actor ->
                    linkedMapOf<String, Any?>(
                        "id" to actor.id,
                        "name" to actor.name,
                        "level" to actor.level,
                        "hp" to actor.hp,
                        "mhp" to actor.maxHp,
                        "mp" to actor.mp,
                        "mmp" to actor.maxMp,
                        "inParty" to actor.inParty,
                    ).apply { putAll(actor.stats) }
                }) }
                "setActorStat" -> cheats.setActorValue(
                    argInt(a, 0), argString(a, 1), argString(a, 2),
                ) { completeMessage(it, done) }
                "recoverActor" -> cheats.recoverActor(argInt(a, 0)) { completeMessage(it, done) }
                "setActorParty" -> cheats.setActorParty(argInt(a, 0), argBoolean(a, 1)) {
                    completeMessage(it, done) { done(argBoolean(a, 1)) }
                }
                "recoverParty" -> cheats.runAction("recoverParty") { message ->
                    completeMessage(message, done) { cheats.listActors { actors ->
                        done(actors.map { actor -> actorMap(actor) })
                    } }
                }
                "actorSkills", "actorStates" -> actorCollection(
                    argInt(a, 0), if (method == "actorSkills") "skills" else "states", done,
                )
                "learnSkill", "forgetSkill", "addState", "removeState" -> changeActorCollection(method, a, done)
                "actorEquips" -> cheats.listActorEquips(argInt(a, 0)) { entries -> done(entries.map {
                    mapOf("slot" to it.index, "id" to it.itemId, "name" to it.itemName, "type" to it.kind)
                }) }
                "changeEquip" -> cheats.setActorEquip(
                    argInt(a, 0), argInt(a, 1), argString(a, 2), argInt(a, 3),
                ) { completeMessage(it, done) }
                "listSystem" -> listSystem(argString(a, 0), done)
                "setSwitch" -> cheats.setSystemSwitch(argInt(a, 0), argBoolean(a, 1)) {
                    completeMessage(it, done) { done(argBoolean(a, 1)) }
                }
                "setVariable" -> cheats.setSystemVariable(argInt(a, 0), rubyValue(a.opt(1))) {
                    completeMessage(it, done) { done(a.opt(1)) }
                }
                "listMaps" -> listMaps(a, done)
                "currentPosition" -> currentPosition(done)
                "teleport" -> cheats.warpToPosition(
                    argInt(a, 0), argInt(a, 1), argInt(a, 2),
                ) { completeMessage(it, done) }
                "minimapData" -> miniMapData(done)
                "listEvents" -> cheats.listEvents(argInt(a, 0)) { events -> done(events.map { eventMap(it) }) }
                "eventDetail" -> eventDetail(argInt(a, 0), argInt(a, 1), done)
                "eventInterpreter" -> eventInterpreter(argInt(a, 0), argInt(a, 1), argInt(a, 2), done)
                "setEventSelfSwitches" -> cheats.setEventSelfSwitches(
                    argInt(a, 0), argBoolean(a, 1), argInt(a, 2),
                ) { completeMessage(it, done) { done(argBoolean(a, 1)) } }
                "setMapEventSelfSwitches" -> cheats.setMapEventSelfSwitches(
                    argInt(a, 0), argBoolean(a, 1),
                ) { completeMessage(it, done) { done(argBoolean(a, 1)) } }
                "battleSnapshot" -> battleSnapshot(done)
                "setEnemyHp" -> cheats.setEnemyHp(argInt(a, 0), argInt(a, 1)) {
                    completeMessage(it, done)
                }
                "killAllEnemies" -> cheats.runAction("killEnemies") { completeMessage(it, done) }
                "forceVictory" -> cheats.runAction("forceVictory") { completeMessage(it, done) }
                "forceDefeat" -> cheats.runAction("forceDefeat") { completeMessage(it, done) }
                "forceEscape" -> cheats.runAction("forceEscape") { completeMessage(it, done) }
                "listSaveSlots" -> cheats.listSaveSlots { slots -> done(slots.map { slot ->
                    mapOf(
                        "id" to slot.id,
                        "exists" to slot.exists,
                        "title" to if (slot.exists) slot.label else "",
                        "playtime" to "",
                        "timestamp" to slot.modified,
                    )
                }) }
                "saveGame" -> cheats.runAction("saveSlot:${argInt(a, 0)}") { completeMessage(it, done) }
                "loadGame" -> cheats.runAction("loadSlot:${argInt(a, 0)}") { completeMessage(it, done) }
                "deleteSave" -> cheats.deleteSaveSlot(argInt(a, 0)) { completeMessage(it, done) }
                else -> done(JsFailure("RGSS 修改器不支持此调用：$method"))
            }
        }

        private fun setFlag(name: String, enabled: Boolean, done: (Any?) -> Unit) {
            val toggle = when (name) {
                "godMode" -> TyranorCheatToggle.INVINCIBILITY
                "hpmp" -> TyranorCheatToggle.UNLIMITED_HPMP
                "tp" -> TyranorCheatToggle.UNLIMITED_TP
                "points" -> TyranorCheatToggle.CUSTOM_POINTS_LOCK
                "gold" -> TyranorCheatToggle.UNLIMITED_GOLD
                "coins" -> TyranorCheatToggle.UNLIMITED_COINS
                "status" -> TyranorCheatToggle.STATUS_IMMUNITY
                "items" -> TyranorCheatToggle.UNLIMITED_ITEMS
                "oneHit" -> TyranorCheatToggle.ONE_HIT_KILL
                "alwaysCrit" -> TyranorCheatToggle.ALWAYS_CRIT
                "noclip" -> TyranorCheatToggle.WALK_THROUGH_WALLS
                "noEncounter" -> TyranorCheatToggle.NO_ENCOUNTERS
                "speed" -> TyranorCheatToggle.SPEED_BOOST
                "fastText" -> TyranorCheatToggle.FAST_TEXT
                "eventSpeed" -> TyranorCheatToggle.EVENT_SPEED
                "msgSkip" -> TyranorCheatToggle.MESSAGE_SKIP
                "escape" -> TyranorCheatToggle.ESCAPE_ALWAYS
                "freeSkill" -> TyranorCheatToggle.FREE_SKILL_COST
                else -> {
                    done(JsFailure("未知作弊开关：$name"))
                    return
                }
            }
            cheats.setEnabled(toggle, enabled) { message ->
                completeMessage(message, done) { done(cheats.isEnabled(toggle)) }
            }
        }

        private fun listDatabase(a: JSONArray, done: (Any?) -> Unit) {
            val kind = argString(a, 0, "item")
            val query = argString(a, 1).trim()
            val offset = argInt(a, 2).coerceAtLeast(0)
            val limit = argInt(a, 3, 100).coerceIn(1, 300)
            cheats.listPickables { entries ->
                val filtered = entries.asSequence()
                    .filter { it.kind == kind }
                    .filter { query.isEmpty() || it.name.contains(query, true) ||
                        it.description.contains(query, true) || it.key.contains(query, true) }
                    .toList()
                done(mapOf(
                    "total" to filtered.size,
                    "values" to filtered.drop(offset).take(limit).map {
                        mapOf("id" to it.key, "name" to it.name, "description" to it.description, "count" to it.quantity)
                    },
                ))
            }
        }

        private fun itemCount(kind: String, key: String, done: (Any?) -> Unit) {
            cheats.listPickables { entries ->
                done(entries.firstOrNull { it.kind == kind && it.key == key }?.quantity ?: 0)
            }
        }

        private fun actorMap(actor: TyranorCheatController.ActorEntry): Map<String, Any?> =
            linkedMapOf<String, Any?>(
                "id" to actor.id,
                "name" to actor.name,
                "level" to actor.level,
                "hp" to actor.hp,
                "mhp" to actor.maxHp,
                "mp" to actor.mp,
                "mmp" to actor.maxMp,
                "inParty" to actor.inParty,
            ).apply { putAll(actor.stats) }

        private fun actorCollection(actorId: Int, collection: String, done: (Any?) -> Unit) {
            cheats.listActorCollection(actorId, collection) { entries ->
                done(entries.map { mapOf("id" to it.id, "name" to it.name, "enabled" to it.enabled) })
            }
        }

        private fun changeActorCollection(method: String, a: JSONArray, done: (Any?) -> Unit) {
            val actorId = argInt(a, 0)
            val itemId = argInt(a, 1)
            val (collection, enabled) = when (method) {
                "learnSkill" -> "skills" to true
                "forgetSkill" -> "skills" to false
                "addState" -> "states" to true
                else -> "states" to false
            }
            cheats.setActorCollection(actorId, collection, itemId, enabled) { message ->
                completeMessage(message, done) { actorCollection(actorId, collection, done) }
            }
        }

        private fun listSystem(kind: String, done: (Any?) -> Unit) {
            cheats.listSystem { entries ->
                done(entries.filter { it.kind == kind }.map { entry ->
                    mapOf(
                        "id" to entry.id,
                        "name" to entry.name.ifBlank { if (kind == "switch") "开关 ${entry.id}" else "变量 ${entry.id}" },
                        "value" to if (kind == "switch") entry.value == "true" else parseSystemValue(entry.valueType, entry.value),
                    )
                })
            }
        }

        private fun listMaps(a: JSONArray, done: (Any?) -> Unit) {
            val query = argString(a, 0)
            val offset = argInt(a, 1).coerceAtLeast(0)
            val limit = argInt(a, 2, 100).coerceIn(1, 300)
            cheats.listMaps { entries ->
                val filtered = entries.filter {
                    query.isBlank() || it.name.contains(query, true) || it.id.toString().contains(query)
                }
                done(mapOf("total" to filtered.size, "values" to filtered.drop(offset).take(limit).map { mapEntry ->
                    mapOf(
                        "id" to mapEntry.id,
                        "name" to mapEntry.name,
                        "depth" to mapEntry.depth,
                        "current" to mapEntry.isCurrent,
                        "currentX" to mapEntry.currentX,
                        "currentY" to mapEntry.currentY,
                    )
                }))
            }
        }

        private fun currentPosition(done: (Any?) -> Unit) {
            cheats.listMaps { maps ->
                val current = maps.firstOrNull { it.isCurrent && it.currentX != null && it.currentY != null }
                done(current?.let {
                    mapOf("mapId" to it.id, "x" to it.currentX, "y" to it.currentY, "direction" to (it.currentDirection ?: 2))
                })
            }
        }

        private fun miniMapData(done: (Any?) -> Unit) {
            cheats.listMaps { maps ->
                val mapId = maps.firstOrNull { it.isCurrent }?.id ?: 0
                cheats.loadMiniMap { miniMap ->
                    if (miniMap == null) {
                        done(JsFailure("当前地图没有可用的迷你地图数据"))
                    } else {
                        done(mapOf(
                            "mapId" to mapId,
                            "width" to miniMap.width,
                            "height" to miniMap.height,
                            "rows" to miniMap.rows,
                            "player" to mapOf("x" to miniMap.playerX, "y" to miniMap.playerY),
                            "events" to miniMap.events.map { mapOf("id" to it.id, "x" to it.x, "y" to it.y) },
                        ))
                    }
                }
            }
        }

        private fun eventDetail(eventId: Int, mapId: Int, done: (Any?) -> Unit) {
            cheats.loadEventDetail(mapId, eventId) { event ->
                done(event?.let { detail ->
                    mapOf(
                        "id" to detail.id,
                        "mapId" to detail.mapId,
                        "name" to detail.name,
                        "x" to detail.x,
                        "y" to detail.y,
                        "description" to detail.description,
                        "selfSwitches" to detail.selfSwitches,
                        "pages" to detail.pages.map { page ->
                            mapOf(
                                "index" to page.index,
                                "validCount" to page.validCount,
                                "commandCount" to page.commandCount,
                                "conditions" to page.conditions.map { condition ->
                                    mapOf(
                                        "type" to condition.type,
                                        "id" to condition.id,
                                        "target" to condition.target,
                                        "current" to condition.current,
                                        "met" to condition.met,
                                        "name" to condition.name,
                                    )
                                },
                            )
                        },
                    )
                } ?: JsFailure("地图 $mapId 找不到事件 #$eventId"))
            }
        }

        private fun eventInterpreter(eventId: Int, mapId: Int, pageIndex: Int, done: (Any?) -> Unit) {
            cheats.loadEventInterpreter(mapId, eventId, pageIndex) { commands ->
                if (commands == null) {
                    done(JsFailure("无法读取所选事件页的解释器"))
                } else {
                    done(commands.map { command ->
                        mapOf(
                            "index" to command.index,
                            "code" to command.code,
                            "indent" to command.indent,
                            "preview" to command.preview,
                            "parameters" to command.parameters,
                        )
                    })
                }
            }
        }

        private fun eventMap(event: TyranorCheatController.EventEntry): Map<String, Any?> = mapOf(
            "id" to event.id,
            "mapId" to event.mapId,
            "name" to event.name,
            "x" to event.x,
            "y" to event.y,
            "pages" to event.pageCount,
            "firstPageLength" to event.firstPageCommandCount,
            "selfSwitches" to event.selfSwitches,
        )

        private fun battleSnapshot(done: (Any?) -> Unit) {
            cheats.listBattleEnemies { enemies ->
                cheats.listBattleActors { actors ->
                    val inBattle = enemies.isNotEmpty() || actors.isNotEmpty()
                    done(mapOf(
                        "inBattle" to inBattle,
                        "enemies" to enemies.map { mapOf("index" to it.index, "name" to it.name, "hp" to it.hp, "mhp" to it.maxHp) },
                        "actors" to actors.map { mapOf("index" to it.index, "name" to it.name, "hp" to it.hp, "mhp" to it.maxHp) },
                    ))
                }
            }
        }

        private fun completeMessage(message: String, done: (Any?) -> Unit, onSuccess: () -> Unit = { done(true) }) {
            if (isFailureMessage(message)) done(JsFailure(message)) else onSuccess()
        }

        private fun isFailureMessage(message: String): Boolean {
            val value = message.trim()
            if (value.isEmpty() || value == "ok" || value == "true") return false
            return value.startsWith("This cheat doesn't work", true) ||
                value.startsWith("Cheat failed:", true) ||
                value.startsWith("Couldn't", true) ||
                value.startsWith("Could not", true) ||
                value.startsWith("Enter ", true) ||
                value.startsWith("Choose ", true) ||
                value.startsWith("Unknown ", true) ||
                value.startsWith("You are not", true) ||
                value.startsWith("You have no", true) ||
                value.startsWith("Your party", true) ||
                value.startsWith("That ", true) ||
                value.startsWith("No ", true) ||
                value.startsWith("Start or load", true) ||
                value.startsWith("Trainer data isn't", true) ||
                value.startsWith("The current", true) ||
                value.startsWith("The game", true) && value.contains("couldn't", true) ||
                value.startsWith("This game", true) && (
                    value.contains("doesn't", true) || value.contains("does not", true) || value.contains("isn't", true) ||
                        value.contains("is not", true) || value.contains("couldn't", true) || value.contains("isn't available", true)
                    ) ||
                value.startsWith("Map warp failed:", true)
        }

        private fun argString(args: JSONArray, index: Int, default: String = ""): String {
            val value = args.opt(index)
            return if (value == null || value === JSONObject.NULL) default else value.toString()
        }

        private fun argInt(args: JSONArray, index: Int, default: Int = 0): Int =
            argString(args, index).toDoubleOrNull()?.toInt() ?: default

        private fun argBoolean(args: JSONArray, index: Int): Boolean {
            val value = args.opt(index)
            return when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value?.toString()?.equals("true", true) == true || value?.toString() == "1"
            }
        }

        private fun rubyValue(value: Any?): String = when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject, is JSONArray -> value.toString()
            else -> value.toString()
        }

        private fun parseSystemValue(type: String, raw: String): Any? {
            when (type.lowercase()) {
                "boolean" -> return raw == "true"
                "number" -> return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
                "string", "complex" -> return raw
                "json" -> return runCatching { JSONTokener(raw).nextValue() }.getOrDefault(raw)
            }
            if (type.contains("float", true) || type.contains("double", true)) return raw.toDoubleOrNull() ?: raw
            if (type.contains("int", true) || type.contains("number", true)) return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
            return raw
        }

        private fun jsonValue(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is JsFailure -> JSONObject.NULL
            is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, item) -> if (key != null) put(key.toString(), jsonValue(item)) }
            }
            is Iterable<*> -> JSONArray().apply { value.forEach { put(jsonValue(it)) } }
            else -> value.toString()
        }

        private fun errorJson(message: String): String = JSONObject().put("__error", message).toString()
    }

    private data class JsFailure(val message: String)

    private companion object {
        const val TAG = "TyranorMvmzCheat"
        const val PANEL_ASSET = "tyranor_rgss_cheats.html"
        const val CHEAT_BRIDGE = "TyranorCheatNative"
        const val INPUT_BRIDGE = "TyranorInputNative"
        const val COMMAND_WAIT_SECONDS = 36L
    }
}
