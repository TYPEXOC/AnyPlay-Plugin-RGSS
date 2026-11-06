package com.tyranor.runtime.input

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout

/** Runs TYN's configurable pad over the SDL surface and writes edits through the granted profile URI. */
class TyranorPadController(
    private val activity: Activity,
    private val parent: FrameLayout,
    profile: PadProfile,
    private val profileUri: Uri?,
    enabled: Boolean,
    primaryColor: Int,
    onPrimaryColor: Int,
) {
    private val bridge = TynSdlInputBridge()
    private val sink = object : InputSink {
        private val held = LinkedHashSet<Int>()

        override fun send(key: Int, down: Boolean) {
            if (CanonicalKeys.isMouse(key) || CanonicalKeys.isAction(key)) return
            if (down) {
                if (held.add(key)) bridge.sendKeyDown(key)
            } else if (held.remove(key)) {
                bridge.sendKeyUp(key)
            }
        }

        override fun releaseAll() {
            held.toList().asReversed().forEach { bridge.sendKeyUp(it) }
            held.clear()
        }
    }

    val pad = VirtualPadView(activity, sink).apply {
        theme = VirtualPadView.PadTheme(primary = primaryColor, onPrimary = onPrimaryColor)
        this.profile = profile
        visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private var editPanel: VirtualPadEditPanel? = null

    init {
        parent.addView(
            pad,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pad.listener = object : VirtualPadView.Listener {
            override fun onEditStarted() {
                if (editPanel == null) {
                    editPanel = VirtualPadEditPanel.attach(
                        parent = parent,
                        context = activity,
                        pad = pad,
                        theme = pad.theme,
                        onSave = { pad.commitEdit() },
                        onCancel = { pad.cancelEdit() },
                    )
                }
            }

            override fun onEditEnded() {
                editPanel?.dismiss()
                editPanel = null
            }

            override fun onSelectionChanged(info: VirtualPadView.SelectionInfo?) {
                editPanel?.refresh()
            }

            override fun onProfileCommitted(profile: PadProfile) {
                pad.profile = profile
                persistProfile(profile)
            }

            override fun onPadVisibilityChanged(visible: Boolean) = Unit
        }
    }

    fun handleBack(): Boolean {
        if (!pad.isEditing) return false
        pad.cancelEdit()
        return true
    }

    fun releaseAllKeys() = pad.releaseAllKeys()

    fun onPause() = pad.onHostPause()

    fun shutdown() {
        editPanel?.dismiss()
        editPanel = null
        pad.listener = null
        pad.detach()
        sink.releaseAll()
    }

    private fun persistProfile(profile: PadProfile) {
        val json = profile.toJson()
        runCatching {
            activity.filesDir.resolve("tyranor-pad-profile.json").writeText(json)
        }
        val uri = profileUri ?: return
        runCatching {
            activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(json)
            }
        }
    }
}
