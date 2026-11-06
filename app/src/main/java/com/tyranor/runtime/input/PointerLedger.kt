package com.tyranor.runtime.input

/** Tracks which touch pointers currently hold each virtual button. */
internal class PointerLedger {

    private val targets = HashMap<Int, String>()
    private val buttons = HashMap<Int, PadButton>()

    val size: Int get() = targets.size

    fun isEmpty(): Boolean = targets.isEmpty()

    fun isNotEmpty(): Boolean = targets.isNotEmpty()

    fun press(pointerId: Int, button: PadButton) {
        targets[pointerId] = button.id
        buttons[pointerId] = button
    }

    fun buttonOf(pointerId: Int): PadButton? = buttons[pointerId]

    fun isHeldById(buttonId: String): Boolean = buttons.values.any { it.id == buttonId }

    fun retainOnly(keep: (pointerId: Int) -> Boolean): List<PadButton> {
        val removed = ArrayList<PadButton>()
        val iterator = targets.entries.iterator()
        while (iterator.hasNext()) {
            val (pointerId, _) = iterator.next()
            if (keep(pointerId)) continue
            iterator.remove()
            buttons.remove(pointerId)?.let(removed::add)
        }
        return removed
    }

    fun release(pointerId: Int): PadButton? {
        targets.remove(pointerId)
        return buttons.remove(pointerId)
    }

    fun allButtons(): List<PadButton> = buttons.values.distinctBy { it.id }

    fun clear() {
        targets.clear()
        buttons.clear()
    }
}
