package com.tyranor.runtime.cheat

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/** The three checkbox sections of the cheat panel. */
enum class TyranorCheatSection(val title: String) {
    CHARACTER("Character states"),
    EXPLORATION("Exploration"),
    BATTLE("Battle")
}

/**
 * Every checkbox cheat. [wireName] MUST match the names used in
 * rb/anyplay_prelude.rb (module TyranorCheats) exactly.
 */
enum class TyranorCheatToggle(
    val wireName: String,
    val label: String,
    val section: TyranorCheatSection
) {
    // Character states
    UNLIMITED_HPMP("hpmp", "Unlimited HP/MP", TyranorCheatSection.CHARACTER),
    UNLIMITED_TP("tp", "Unlimited TP", TyranorCheatSection.CHARACTER),
    UNLIMITED_GOLD("gold", "Unlimited Gold", TyranorCheatSection.CHARACTER),
    STATUS_IMMUNITY("status", "Status ailment immunity", TyranorCheatSection.CHARACTER),
    UNLIMITED_ITEMS("items", "Unlimited Items", TyranorCheatSection.CHARACTER),

    // Exploration
    WALK_THROUGH_WALLS("noclip", "Walk through walls", TyranorCheatSection.EXPLORATION),
    NO_ENCOUNTERS("noEncounter", "No random encounters", TyranorCheatSection.EXPLORATION),
    SPEED_BOOST("speed", "Speed boost", TyranorCheatSection.EXPLORATION),
    FAST_TEXT("fastText", "Fast text speed", TyranorCheatSection.EXPLORATION),
    EVENT_SPEED("eventSpeed", "Speed up events", TyranorCheatSection.EXPLORATION),
    MESSAGE_SKIP("msgSkip", "Skip message pauses", TyranorCheatSection.EXPLORATION),

    // Battle
    ONE_HIT_KILL("oneHit", "One-hit kill", TyranorCheatSection.BATTLE),
    INVINCIBILITY("invincible", "Invincibility", TyranorCheatSection.BATTLE),
    ALWAYS_CRIT("crit", "Always critical hit", TyranorCheatSection.BATTLE),
    ESCAPE_ALWAYS("escape", "Escape always works", TyranorCheatSection.BATTLE),
    FREE_SKILL_COST("freeSkill", "Free skill cost (SP/MP/TP/PP)", TyranorCheatSection.BATTLE)
}

/**
 * TyranorCheatController (RGSS plugin version)
 * ----------------------------------
 * Same public API as the AnyPlay app's TyranorCheatController, but the cheats run
 * inside the game's Ruby code (TyranorCheats in anyplay_prelude.rb) instead
 * of JavaScript.
 *
 * Talking to the game: one command at a time is written to a small file in
 * the app cache folder; the game picks it up a few times per second, runs
 * it and writes the answer into a reply file. Commands wait in a queue so
 * quick taps never overwrite each other.
 *
 * Must be used from the main thread.
 */
class TyranorCheatController(
    context: Context,
    gameKey: String
) {
    data class Capabilities(
        val engine: String,
        val toggles: Set<String>,
        val values: Set<String>,
        val features: Set<String> = emptySet(),
        val actorValues: Set<String> = emptySet()
    )

    data class SystemEntry(
        val kind: String,
        val id: Int,
        val name: String,
        val valueType: String,
        val value: String
    )

    data class MapEntry(
        val id: Int,
        val name: String,
        val isCurrent: Boolean,
        val depth: Int,
        val currentX: Int?,
        val currentY: Int?,
        val currentDirection: Int?
    )

    data class ActorEntry(
        val id: Int,
        val name: String,
        val level: Int,
        val hp: Int,
        val maxHp: Int,
        val mp: Int,
        val maxMp: Int,
        val stats: Map<String, Int> = emptyMap()
    )

    data class PokemonEntry(
        val index: Int,
        val name: String,
        val level: Int,
        val hp: Int,
        val maxHp: Int,
        val exp: Int,
        val isEgg: Boolean,
        val status: String = "",
        val heldItem: String = ""
    )

    data class PokemonMoveEntry(val slot: Int, val key: String, val name: String)
    data class PokemonMoveCandidate(val key: String, val name: String)
    data class PokemonStatusEntry(val key: String, val active: Boolean)
    data class PokemonStatValues(val ivs: List<Int>, val evs: List<Int>)

    data class EventEntry(
        val mapId: Int,
        val id: Int,
        val name: String,
        val x: Int,
        val y: Int,
        val selfSwitches: Map<String, Boolean>,
        val pageCount: Int,
        val firstPageCommandCount: Int
    )

    data class MiniMapEvent(
        val id: Int,
        val x: Int,
        val y: Int
    )

    data class MiniMap(
        val width: Int,
        val height: Int,
        val playerX: Int,
        val playerY: Int,
        val rows: List<String>,
        val events: List<MiniMapEvent>
    )

    data class BattleEnemy(
        val index: Int,
        val name: String,
        val hp: Int,
        val maxHp: Int
    )

    data class BattleActor(
        val index: Int,
        val name: String,
        val hp: Int,
        val maxHp: Int
    )

    data class ActorCollectionEntry(
        val kind: String,
        val id: Int,
        val enabled: Boolean,
        val name: String
    )

    data class SaveSlot(
        val id: Int,
        val exists: Boolean,
        val isCurrent: Boolean,
        val modified: Long,
        val label: String
    )

    data class EquipSlot(
        val index: Int,
        val itemId: Int,
        val kind: String,
        val slotName: String,
        val itemName: String
    )

    data class EquipCandidate(val kind: String, val itemId: Int, val name: String)

    private val prefs =
        context.applicationContext.getSharedPreferences("tyranor_cheats", Context.MODE_PRIVATE)

    private val keyBase = "game_${gameKey.hashCode()}"
    private val togglesKey = "$keyBase.toggles"
    private val speedKey = "$keyBase.speed"

    private val dir = context.applicationContext.cacheDir
    private val cmdFile = File(dir, "tyranor_cheat_cmd.txt")
    private val tmpFile = File(dir, "tyranor_cheat_cmd.tmp")
    private val replyFile = File(dir, "tyranor_cheat_reply.txt")
    private val handler = Handler(Looper.getMainLooper())

    private class Pending(
        val id: Int,
        val line: String,
        val deadline: Long, // 0 = wait forever
        val callback: ((String) -> Unit)?
    ) {
        var sent = false
    }

    private val queue = ArrayDeque<Pending>()
    private var nextId = 1
    private var ready = false
    private var polling = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            val head = queue.firstOrNull()
            if (head == null) {
                polling = false
                return
            }
            var answered = false
            try {
                if (replyFile.exists()) {
                    val text = replyFile.readText()
                    val parts = text.split('\t', limit = 2)
                    if (parts[0].trim() == head.id.toString()) {
                        replyFile.delete()
                        queue.removeFirst()
                        answered = true
                        head.callback?.invoke(parts.getOrElse(1) { "" })
                    } else if (parts[0].trim().toIntOrNull()?.let { it < head.id } == true) {
                        replyFile.delete() // old answer, nobody waits for it
                    }
                }
            } catch (e: Exception) {
                // File still being written; try again next round.
            }
            if (!answered && head.deadline != 0L && System.currentTimeMillis() > head.deadline) {
                queue.removeFirst()
                cmdFile.delete()
                head.callback?.invoke("The game didn't answer. Try again.")
                answered = true
            }
            if (answered) pump()
            handler.postDelayed(this, 80)
        }
    }

    init {
        cmdFile.delete()
        tmpFile.delete()
        replyFile.delete()
        // Gives the game the saved checkboxes as soon as it starts running.
        enqueue("restore\t${speedMultiplier()}\t${enabledNames().joinToString(",")}", 0L) { ready = true }
    }

    private fun enabledNames(): Set<String> =
        prefs.getStringSet(togglesKey, emptySet())?.toSet() ?: emptySet()

    fun isEnabled(toggle: TyranorCheatToggle): Boolean = enabledNames().contains(toggle.wireName)

    /** The Ruby command channel has completed its initial state restore. */
    fun isReady(): Boolean = ready

    fun speedMultiplier(): Float = prefs.getFloat(speedKey, 2f)

    /**
     * Checkbox tapped. [onResult] gets "" when it worked, otherwise a message
     * (for example "This cheat doesn't work in this type of game.") and the
     * checkbox must go back to OFF.
     */
    fun setEnabled(toggle: TyranorCheatToggle, enabled: Boolean, onResult: (String) -> Unit = {}) {
        send("toggle\t${toggle.wireName}\t${if (enabled) "1" else "0"}") { msg ->
            if (msg.isEmpty()) {
                val updated = enabledNames().toMutableSet()
                if (enabled) updated.add(toggle.wireName) else updated.remove(toggle.wireName)
                prefs.edit().putStringSet(togglesKey, updated).apply()
            } else if (!enabled) {
                // Turning OFF can never really fail; keep the saved state honest.
                val updated = enabledNames().toMutableSet().apply { remove(toggle.wireName) }
                prefs.edit().putStringSet(togglesKey, updated).apply()
            }
            onResult(msg)
        }
    }

    /**
     * "Set" button on a value row. [name] is gold, level, exp, atk, def, mat,
     * mdf, agi, luk or speed.
     */
    fun setValue(name: String, a: String, onResult: (String) -> Unit) {
        send("value\t$name\t${clean(a)}") { msg ->
            if (name == "speed" && msg.startsWith("Speed x")) {
                a.trim().toFloatOrNull()?.takeIf { it > 0f }?.let {
                    prefs.edit().putFloat(speedKey, it.coerceIn(0.25f, 8f)).apply()
                    val updated = enabledNames().toMutableSet().apply { add(TyranorCheatToggle.SPEED_BOOST.wireName) }
                    prefs.edit().putStringSet(togglesKey, updated).apply()
                }
            }
            onResult(msg)
        }
    }

    /** Gives the picked thing. [kind] is item / weapon / armor / pokemon, [amount] is the quantity (or the level for a Pokemon). */
    fun give(kind: String, key: String, amount: String, onResult: (String) -> Unit) {
        send("give\t$kind\t${clean(key)}\t${clean(amount)}", onResult)
    }

    /** One-click button (maxExp, instantWin, fullInventory). */
    fun runAction(name: String, onResult: (String) -> Unit) {
        val timeout = when {
            name == "saveScreen" || name == "loadScreen" -> 0L
            name.startsWith("saveSlot:") || name.startsWith("loadSlot:") -> 0L
            name.startsWith("warp:") -> 30_000L
            else -> 8000L
        }
        send("run\t$name", timeout, onResult)
    }

    fun warpToMap(mapId: Int, onResult: (String) -> Unit) {
        send("run\twarp:$mapId", onResult)
    }

    fun warpToPosition(mapId: Int, x: Int, y: Int, onResult: (String) -> Unit) {
        send("run\twarp:$mapId\t$x\t$y", onResult)
    }

    fun setItemQuantity(kind: String, key: String, quantity: Int, onResult: (String) -> Unit) {
        send("quantity\t$kind\t${clean(key)}\t$quantity", onResult)
    }

    fun setActorValue(actorId: Int, field: String, value: String, onResult: (String) -> Unit) {
        send("actor_value\t$actorId\t$field\t${clean(value)}", onResult)
    }

    fun recoverActor(actorId: Int, onResult: (String) -> Unit) {
        send("actor_recover\t$actorId", onResult)
    }

    fun setActorCollection(actorId: Int, collection: String, itemId: Int, enabled: Boolean, onResult: (String) -> Unit) {
        send("actor_collection\t$actorId\t$collection\t$itemId\t${if (enabled) "1" else "0"}", onResult)
    }

    fun listActorEquips(actorId: Int, onResult: (List<EquipSlot>) -> Unit) {
        send("list\tactor_equips\t$actorId", 30_000L) { raw ->
            val out = mutableListOf<EquipSlot>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 6)
                if (p.size != 6 || p[0] != "Q") return@forEach
                val index = p[1].toIntOrNull() ?: return@forEach
                out.add(EquipSlot(index, p[2].toIntOrNull() ?: 0, p[3], p[4], p[5]))
            }
            onResult(out)
        }
    }

    fun listEquipCandidates(actorId: Int, slot: Int, onResult: (List<EquipCandidate>) -> Unit) {
        send("list\tequip_candidates\t$actorId\t$slot", 30_000L) { raw ->
            val out = mutableListOf<EquipCandidate>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 4)
                if (p.size != 4 || p[0] != "Q") return@forEach
                out.add(EquipCandidate(p[1], p[2].toIntOrNull() ?: 0, p[3]))
            }
            onResult(out)
        }
    }

    fun setActorEquip(actorId: Int, slot: Int, kind: String, itemId: Int, onResult: (String) -> Unit) {
        send("actor_equip\t$actorId\t$slot\t$kind\t$itemId", onResult)
    }

    fun setEventSelfSwitch(eventId: Int, letter: String, enabled: Boolean, onResult: (String) -> Unit) {
        send("event_switch\t$eventId\t$letter\t${if (enabled) "1" else "0"}", onResult)
    }

    fun setEventSelfSwitches(eventId: Int, enabled: Boolean, onResult: (String) -> Unit) {
        send("event_switch\t$eventId\tALL\t${if (enabled) "1" else "0"}", onResult)
    }

    fun setEnemyHp(index: Int, hp: Int, onResult: (String) -> Unit) {
        send("battle_enemy_hp\t$index\t$hp", onResult)
    }

    fun setSystemSwitch(id: Int, enabled: Boolean, onResult: (String) -> Unit) {
        send("system_switch\t$id\t${if (enabled) "1" else "0"}", onResult)
    }

    fun setSystemVariable(id: Int, value: String, onResult: (String) -> Unit) {
        send("system_variable\t$id\t${cleanPayload(value)}", onResult)
    }

    fun loadCapabilities(onResult: (Capabilities) -> Unit) {
        send("list\tcapabilities") { raw ->
            var engine = "unknown"
            val toggles = mutableSetOf<String>()
            val values = mutableSetOf<String>()
            val features = mutableSetOf<String>()
            val actorValues = mutableSetOf<String>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 2)
                if (p.size != 2) return@forEach
                when (p[0]) {
                    "E" -> engine = p[1]
                    "T" -> toggles.add(p[1])
                    "V" -> values.add(p[1])
                    "F" -> features.add(p[1])
                    "AS" -> actorValues.add(p[1])
                }
            }
            onResult(Capabilities(engine, toggles, values, features, actorValues))
        }
    }

    fun getGold(onResult: (Int?) -> Unit) {
        send("list\tgold") { raw ->
            val fields = raw.lineSequence().firstOrNull()?.split('\t', limit = 2)
            onResult(if (fields?.size == 2 && fields[0] == "G") fields[1].toIntOrNull() else null)
        }
    }

    fun listActors(onResult: (List<ActorEntry>) -> Unit) {
        send("list\tactors", 30_000L) { raw ->
            val out = mutableListOf<ActorEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t')
                if (p.size < 8 || p[0] != "A") return@forEach
                val id = p[1].toIntOrNull() ?: return@forEach
                val stats = p.drop(8).mapNotNull { field ->
                    val pair = field.split('=', limit = 2)
                    if (pair.size != 2) return@mapNotNull null
                    pair[1].toIntOrNull()?.let { pair[0] to it }
                }.toMap()
                out.add(ActorEntry(id, p[2], p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0,
                    p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0, p[7].toIntOrNull() ?: 0, stats))
            }
            onResult(out)
        }
    }

    fun listPokemon(onResult: (List<PokemonEntry>) -> Unit) {
        send("list\tpokemon", 30_000L) { raw ->
            val out = mutableListOf<PokemonEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 10)
                if (p.size != 10 || p[0] != "P") return@forEach
                val index = p[1].toIntOrNull() ?: return@forEach
                out.add(PokemonEntry(index, p[2], p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0,
                    p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0, p[7] == "1", p[8], p[9]))
            }
            onResult(out)
        }
    }

    fun setPokemonValue(index: Int, field: String, value: String, onResult: (String) -> Unit) {
        send("pokemon_value\t$index\t$field\t${clean(value)}", onResult)
    }

    fun recoverPokemon(index: Int, onResult: (String) -> Unit) {
        send("pokemon_recover\t$index", onResult)
    }

    fun listPokemonMoves(index: Int, onResult: (List<PokemonMoveEntry>) -> Unit) {
        send("list\tpokemon_moves\t$index", 30_000L) { raw ->
            val out = mutableListOf<PokemonMoveEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 4)
                val slot = p.getOrNull(1)?.toIntOrNull() ?: return@forEach
                if (p.size == 4 && p[0] == "M") out.add(PokemonMoveEntry(slot, p[2], p[3]))
            }
            onResult(out)
        }
    }

    fun listPokemonMoveCandidates(onResult: (List<PokemonMoveCandidate>) -> Unit) {
        send("list\tpokemon_move_candidates", 30_000L) { raw ->
            val out = mutableListOf<PokemonMoveCandidate>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 3)
                if (p.size == 3 && p[0] == "C") out.add(PokemonMoveCandidate(p[1], p[2]))
            }
            onResult(out)
        }
    }

    fun listPokemonStatuses(index: Int, onResult: (List<PokemonStatusEntry>) -> Unit) {
        send("list\tpokemon_statuses\t$index", 30_000L) { raw ->
            val out = mutableListOf<PokemonStatusEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 3)
                if (p.size == 3 && p[0] == "S") out.add(PokemonStatusEntry(p[1], p[2] == "1"))
            }
            onResult(out)
        }
    }

    fun listPokemonStats(index: Int, onResult: (PokemonStatValues) -> Unit) {
        send("list\tpokemon_stats\t$index", 30_000L) { raw ->
            val ivs = MutableList(6) { 0 }
            val evs = MutableList(6) { 0 }
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 3)
                if (p.size != 3) return@forEach
                val slot = p[1].toIntOrNull()?.takeIf { it in 0..5 } ?: return@forEach
                val value = p[2].toIntOrNull() ?: return@forEach
                when (p[0]) {
                    "I" -> ivs[slot] = value
                    "E" -> evs[slot] = value
                }
            }
            onResult(PokemonStatValues(ivs, evs))
        }
    }

    fun setPokemonMove(index: Int, slot: Int, key: String, onResult: (String) -> Unit) {
        send("pokemon_move\t$index\t$slot\t${clean(key)}", onResult)
    }

    fun setPokemonStatus(index: Int, key: String, onResult: (String) -> Unit) {
        send("pokemon_status\t$index\t${clean(key)}", onResult)
    }

    fun setPokemonItem(index: Int, key: String, onResult: (String) -> Unit) {
        send("pokemon_item\t$index\t${clean(key)}", onResult)
    }

    fun listActorCollection(actorId: Int, collection: String, onResult: (List<ActorCollectionEntry>) -> Unit) {
        send("list\tactor_$collection\t$actorId", 30_000L) { raw ->
            val out = mutableListOf<ActorCollectionEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 5)
                val id = p.getOrNull(2)?.toIntOrNull() ?: return@forEach
                if (p.size == 5 && p[0] == "C") out.add(ActorCollectionEntry(p[1], id, p[3] == "1", p[4]))
            }
            onResult(out)
        }
    }

    fun listSystem(onResult: (List<SystemEntry>) -> Unit) {
        send("list\tsystem", 30_000L) { raw ->
            val out = mutableListOf<SystemEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 5)
                when {
                    p.size == 4 && p[0] == "S" -> {
                        val id = p[1].toIntOrNull() ?: return@forEach
                        out.add(SystemEntry("switch", id, p[3], "boolean", (p[2] == "1").toString()))
                    }
                    p.size == 5 && p[0] == "V" -> {
                        val id = p[1].toIntOrNull() ?: return@forEach
                        out.add(SystemEntry("variable", id, p[4], p[2], p[3]))
                    }
                }
            }
            onResult(out)
        }
    }

    fun listMaps(onResult: (List<MapEntry>) -> Unit) {
        send("list\tmaps", 30_000L) { raw ->
            val out = mutableListOf<MapEntry>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 7)
                if (p.size != 7) return@forEach
                val id = p[0].toIntOrNull() ?: return@forEach
                out.add(MapEntry(id, p[6], p[1] == "1", p[2].toIntOrNull() ?: 0,
                    p[3].toIntOrNull(), p[4].toIntOrNull(), p[5].toIntOrNull()))
            }
            onResult(out)
        }
    }

    fun listEvents(onResult: (List<EventEntry>) -> Unit) {
        send("list\tevents", 30_000L) { raw ->
            val out = mutableListOf<EventEntry>()
            var mapId = 0
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 11)
                if (p.size == 2 && p[0] == "M") {
                    mapId = p[1].toIntOrNull() ?: 0
                    return@forEach
                }
                if (p.size != 11 || p[0] != "E") return@forEach
                val id = p[1].toIntOrNull() ?: return@forEach
                val switches = mapOf("A" to (p[5] == "1"), "B" to (p[6] == "1"), "C" to (p[7] == "1"), "D" to (p[8] == "1"))
                out.add(EventEntry(mapId, id, p[2], p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0,
                    switches, p[9].toIntOrNull() ?: 0, p[10].toIntOrNull() ?: 0))
            }
            onResult(out)
        }
    }

    fun loadMiniMap(onResult: (MiniMap?) -> Unit) {
        send("list\tminimap", 30_000L) { raw ->
            var width = 0
            var height = 0
            var playerX = 0
            var playerY = 0
            val rows = mutableListOf<String>()
            val events = mutableListOf<MiniMapEvent>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 2)
                if (p.size != 2) return@forEach
                if (p[0] == "H") {
                    val h = p[1].split('\t')
                    if (h.size == 4) {
                        width = h[0].toIntOrNull() ?: 0
                        height = h[1].toIntOrNull() ?: 0
                        playerX = h[2].toIntOrNull() ?: 0
                        playerY = h[3].toIntOrNull() ?: 0
                    }
                } else if (p[0] == "R") {
                    rows.add(p[1])
                } else if (p[0] == "E") {
                    val event = p[1].split('\t')
                    if (event.size == 3) {
                        val id = event[0].toIntOrNull()
                        val x = event[1].toIntOrNull()
                        val y = event[2].toIntOrNull()
                        if (id != null && x != null && y != null) events.add(MiniMapEvent(id, x, y))
                    }
                }
            }
            onResult(if (width > 0 && height > 0) MiniMap(width, height, playerX, playerY, rows, events) else null)
        }
    }

    fun listBattleEnemies(onResult: (List<BattleEnemy>) -> Unit) {
        send("list\tbattle", 30_000L) { raw ->
            val out = mutableListOf<BattleEnemy>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 5)
                if (p.size != 5 || p[0] != "B") return@forEach
                val index = p[1].toIntOrNull() ?: return@forEach
                out.add(BattleEnemy(index, p[2], p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0))
            }
            onResult(out)
        }
    }

    fun listBattleActors(onResult: (List<BattleActor>) -> Unit) {
        send("list\tbattle_actors", 30_000L) { raw ->
            val out = mutableListOf<BattleActor>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 5)
                if (p.size != 5 || p[0] != "L") return@forEach
                val index = p[1].toIntOrNull() ?: return@forEach
                out.add(BattleActor(index, p[2], p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0))
            }
            onResult(out)
        }
    }

    fun listSaveSlots(onResult: (List<SaveSlot>) -> Unit) {
        send("list\tsaves", 30_000L) { raw ->
            val out = mutableListOf<SaveSlot>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 6)
                if (p.size != 6 || p[0] != "S") return@forEach
                val id = p[1].toIntOrNull() ?: return@forEach
                out.add(SaveSlot(id, p[2] == "1", p[3] == "1", p[4].toLongOrNull() ?: 0L, p[5]))
            }
            onResult(out)
        }
    }

    fun deleteSaveSlot(id: Int, onResult: (String) -> Unit) {
        send("delete_save\t$id", onResult)
    }

    /** One thing the player can pick in the Give list. */
    data class Pickable(val kind: String, val key: String, val quantity: Int, val name: String, val description: String)

    /**
     * Fetches everything this game can give (items, weapons, armor, and for
     * Pokemon games the Pokemon too) so the picker can show real names.
     * Empty list if the game isn't ready.
     */
    fun listPickables(onResult: (List<Pickable>) -> Unit) {
        send("list\t") { raw ->
            val out = mutableListOf<Pickable>()
            raw.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 5)
                if (p.size >= 4 && p[0].isNotBlank() && p[1].isNotBlank()) {
                    out.add(Pickable(p[0], p[1], p[2].toIntOrNull() ?: 0, p[3], p.getOrNull(4).orEmpty()))
                }
            }
            onResult(out)
        }
    }

    // ---- queue --------------------------------------------------------

    private fun clean(s: String) = s.trim().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    // System variable text is a value, so preserve intentional leading and
    // trailing spaces while keeping the tab-delimited command protocol intact.
    private fun cleanPayload(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun send(line: String, onResult: ((String) -> Unit)?) {
        send(line, 8000L, onResult)
    }

    private fun send(line: String, timeoutMs: Long, onResult: ((String) -> Unit)?) {
        if (!ready) {
            onResult?.invoke("The game is still loading. Try again in a moment.")
            return
        }
        enqueue(line, timeoutMs, onResult)
    }

    private fun enqueue(line: String, timeoutMs: Long, onResult: ((String) -> Unit)?) {
        val deadline = if (timeoutMs == 0L) 0L else System.currentTimeMillis() + timeoutMs
        queue.addLast(Pending(nextId++, line, deadline, onResult))
        pump()
        startPolling()
    }

    /** Writes the first waiting command into the command file. */
    private fun pump() {
        val head = queue.firstOrNull() ?: return
        if (head.sent) return
        try {
            tmpFile.writeText("${head.id}\t${head.line}")
            tmpFile.renameTo(cmdFile)
            head.sent = true
        } catch (e: Exception) {
            queue.removeFirst()
            head.callback?.invoke("Couldn't talk to the game.")
            pump()
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(pollRunnable, 80)
    }

    fun shutdown() {
        handler.removeCallbacks(pollRunnable)
        polling = false
        queue.clear()
    }
}
