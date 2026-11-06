package com.tyranor.runtime.input

/** Tracks asynchronous profile saves so stale results cannot update a later editing session. */
internal class SaveSessionTracker {

    private var sequence = 0
    private var pending = 0

    val hasPending: Boolean get() = pending != 0

    fun begin(): Int {
        val session = ++sequence
        pending = session
        return session
    }

    fun isCurrent(session: Int): Boolean = pending == session

    fun complete(session: Int): Boolean {
        if (pending != session) return false
        pending = 0
        return true
    }

    fun clear() {
        pending = 0
    }
}
