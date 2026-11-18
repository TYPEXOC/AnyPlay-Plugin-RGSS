package com.tyranor.runtime.ui

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import android.util.TypedValue
import com.tyranor.runtime.cheat.TyranorCheatController
import com.tyranor.runtime.input.PadProfile
import com.tyranor.runtime.input.TyranorMouseController
import com.tyranor.runtime.input.TyranorPadController
import com.tyranor.runtime.rgss.R
import java.io.File
import kotlin.math.abs

/**
 * TYN-owned controls drawn over the RGSS game:
 *  - TYN's startup overlay (until the game draws its first frame),
 *  - TYN's configurable virtual pad and edit mode,
 *  - TYN's cheat panel,
 *  - an explicit close-game confirmation.
 *
 * MainActivity only creates one of these and forwards a few calls.
 */
class TynRuntimeUi(
    private val activity: Activity,
    parent: ViewGroup,
    private val initialCheatEngine: String,
    padEnabled: Boolean,
    private val primaryColor: Int,
    private val onPrimaryColor: Int,
    private val darkMode: Boolean,
    cheatsEnabled: Boolean,
    private val copyTextEnabled: Boolean,
    private val speedUp: Int,
    private val sessionDir: File?,
    private val onRestoreGameInputFocus: Runnable,
    private val onExportDiagnostics: Runnable,
) {
    private val density = activity.resources.displayMetrics.density
    private val root = FrameLayout(activity)
    private val loading = FrameLayout(activity)
    private lateinit var loadingTitle: TextView
    private lateinit var loadingHint: TextView
    private lateinit var loadingSpinner: ProgressBar
    private val controller: TyranorPadController
    private val mouseController: TyranorMouseController
    private val cheats = if (cheatsEnabled) TyranorCheatController(activity) else null
    private var cheatMenu: TyranorRgssCheatPanel? = null
    private var actionBar: TyranorRuntimeActionBar? = null
    private var dialog: Dialog? = null
    private var loadingState = LoadingState.LOADING
    private var fastForwardEnabled = false

    private enum class LoadingState { LOADING, REVEALED, FAILED, DISPOSED }

    init {
        parent.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val padProfile = activity.filesDir.resolve("rgss-pad-profile.json")
            .let { file -> runCatching { if (file.isFile) PadProfile.parse(file.readText()) else null }.getOrNull() }
            ?: PadProfile.defaultProfile()
        controller = TyranorPadController(activity, root, padProfile, padEnabled, primaryColor, onPrimaryColor)
        controller.pad.floatingButtonVisible = false
        mouseController = TyranorMouseController(activity, root)
        // Keep the configured virtual-pad hit targets above the transparent mouse layer. Unclaimed
        // touches pass through to the mouse layer when mouse mode is enabled.
        controller.pad.bringToFront()
        // 2) loading spinner (top-most, hides the black screen)
        loading.setBackgroundColor(Color.BLACK)
        loading.isClickable = true
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt())
        }
        loadingTitle = TextView(activity).apply {
            text = activity.getString(R.string.tyn_runtime_loading)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }
        box.addView(loadingTitle, LinearLayout.LayoutParams(-1, -2))
        loadingSpinner = ProgressBar(activity).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(primaryColor)
            contentDescription = activity.getString(R.string.tyn_runtime_loading)
        }
        box.addView(loadingSpinner, LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt()).apply {
            topMargin = (14 * density).toInt()
        })
        loadingHint = TextView(activity).apply {
            text = activity.getString(R.string.tyn_runtime_preparing_game)
            setTextColor(0xFFBDBDBD.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        box.addView(loadingHint, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (12 * density).toInt()
        })
        loading.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        root.addView(loading, FrameLayout.LayoutParams(-1, -1))

        actionBar = TyranorRuntimeActionBar(
            activity,
            root,
            controller,
            mouseController,
            primaryColor,
            onPrimaryColor,
            cheatsEnabled,
        ) { openCheats() }

        if (copyTextEnabled) {
            root.addView(
                actionButton("文", activity.getString(R.string.tyn_runtime_copy_text)) {
                    copyActiveText()
                },
                FrameLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt(), Gravity.TOP or Gravity.END).apply {
                    topMargin = (16 * density).toInt()
                    marginEnd = (16 * density).toInt()
                },
            )
        }

        if (speedUp > 1) {
            val fastForwardButton = actionButton(
                "≫",
                activity.getString(R.string.tyn_runtime_fast_forward, speedUp),
            ) {}
            fastForwardButtonColor(fastForwardButton, enabled = false)
            fastForwardButton.setOnClickListener {
                fastForwardEnabled = !fastForwardEnabled
                fastForwardButtonColor(fastForwardButton, fastForwardEnabled)
                writeFastForwardState()
            }
            root.addView(
                fastForwardButton,
                FrameLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt(), Gravity.TOP or Gravity.END).apply {
                    topMargin = (72 * density).toInt()
                    marginEnd = (16 * density).toInt()
                },
            )
        }

        // Keep the startup mask above all runtime controls until the first frame is rendered.
        loading.bringToFront()

        // Load the local cheat WebView under the startup screen so the first tap does not
        // pay WebView startup and asset parsing costs.
        if (cheats != null) {
            root.post {
                if (cheatMenu == null && !activity.isFinishing && !activity.isDestroyed) {
                    cheatMenu = createCheatMenu(cheats)
                }
            }
        }
    }

    /** Called (from any thread) when the game has drawn its first frame. */
    fun onFirstFrame() {
        activity.runOnUiThread {
            if (loadingState != LoadingState.LOADING) return@runOnUiThread
            loadingState = LoadingState.REVEALED
            loading.animate().alpha(0f).setDuration(150).withEndAction {
                if (loadingState == LoadingState.REVEALED) {
                    loading.alpha = 1f
                    loading.visibility = View.GONE
                }
            }.start()
        }
    }

    /** Shows the same retry state as TYN's native startup overlay. */
    fun onStartupFailed(message: CharSequence? = null) {
        activity.runOnUiThread {
            if (loadingState == LoadingState.FAILED || loadingState == LoadingState.DISPOSED) return@runOnUiThread
            loadingState = LoadingState.FAILED
            loading.animate().cancel()
            loading.alpha = 1f
            loadingTitle.text = activity.getString(R.string.tyn_runtime_launch_failed)
            loadingHint.text = message?.takeIf { it.isNotBlank() }
                ?: activity.getString(R.string.tyn_runtime_return_and_retry)
            loadingSpinner.visibility = View.GONE
            loading.visibility = View.VISIBLE
            loading.bringToFront()
        }
    }

    /** Back button. Returns true when handled (closes edit mode or asks before exiting). */
    fun onBack(): Boolean {
        if (cheatMenu?.handleBack() == true) return true
        if (controller.handleBack()) return true
        showCloseDialog()
        return true
    }

    private fun openCheats() {
        val cheatController = cheats ?: return
        controller.releaseAllKeys()
        val menu = cheatMenu ?: createCheatMenu(cheatController).also { cheatMenu = it }
        menu.show()
    }

    private fun createCheatMenu(cheatController: TyranorCheatController) = TyranorRgssCheatPanel(
        activity,
        root,
        cheatController,
        initialCheatEngine = initialCheatEngine,
        primaryColor = primaryColor,
        onPrimaryColor = onPrimaryColor,
        darkMode = darkMode,
        padAvailable = { true },
        enterPadEdit = {
            controller.setPadVisible(true)
            controller.pad.enterEditMode()
        },
        resetPad = { controller.pad.resetToDefaults() },
        keepRuntimeActionsOnTop = { actionBar?.bringToFront() },
        restoreGameInputFocus = {
            controller.releaseAllKeys()
            onRestoreGameInputFocus.run()
        },
    )

    fun onPause() {
        fastForwardEnabled = false
        writeFastForwardState()
        mouseController.onPause()
        controller.onPause()
    }

    fun onDestroy() {
        loadingState = LoadingState.DISPOSED
        fastForwardEnabled = false
        writeFastForwardState()
        dialog?.dismiss()
        loading.animate().cancel()
        cheatMenu?.destroy()
        cheats?.shutdown()
        mouseController.shutdown()
        controller.shutdown()
    }

    private fun actionButton(label: String, description: String, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            setTextColor(onPrimaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primaryColor)
            }
            elevation = 8f * density
            setOnClickListener { onClick() }
        }

    private fun fastForwardButtonColor(fastForwardButton: TextView, enabled: Boolean) {
        fastForwardButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) primaryColor else 0xAA30343B.toInt())
        }
    }

    private fun copyActiveText() {
        val text = sessionDir?.let { dir ->
            runCatching { File(dir, "tyranor_copy_text.txt").readText() }.getOrNull()
        }.orEmpty()
        if (text.isBlank()) {
            Toast.makeText(activity, R.string.tyn_runtime_copy_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("RPG Maker text", text))
        Toast.makeText(activity, R.string.tyn_runtime_copy_done, Toast.LENGTH_SHORT).show()
    }

    private fun writeFastForwardState() {
        val dir = sessionDir ?: return
        runCatching {
            File(dir, "fast_forward").writeText(if (fastForwardEnabled) "1" else "0")
        }
    }

    // ---- Close-game confirmation -----------------------------------------

    private fun showCloseDialog() {
        if (dialog?.isShowing == true) return
        controller.releaseAllKeys()

        fun round(color: Int, dp: Float) = GradientDrawable().apply {
            setColor(color); cornerRadius = dp * density
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xFF1F2129.toInt(), 22f)
            val p = (24 * density).toInt()
            setPadding(p, p, p, (20 * density).toInt())
        }
        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.tyn_runtime_close_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.tyn_runtime_close_message)
            setTextColor(0xFFB8BCC8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, (12 * density).toInt(), 0, (24 * density).toInt())
        })

        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        fun button(label: String, bg: Int, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = round(bg, 10f)
            setPadding((26 * density).toInt(), (14 * density).toInt(), (26 * density).toInt(), (14 * density).toInt())
            setOnClickListener { onClick() }
        }

        val row = LinearLayout(activity).apply { gravity = Gravity.END }
        row.addView(button(activity.getString(R.string.tyn_runtime_no), 0xFF3A3D48.toInt()) { d.dismiss() })
        row.addView(button(activity.getString(R.string.tyn_runtime_yes), 0xFFE04B4B.toInt()) {
            d.dismiss()
            activity.finish() // MainActivity.onDestroy() ends the process cleanly
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (14 * density).toInt() })
        content.addView(row)

        d.setContentView(content)
        d.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((520 * density).toInt().coerceAtMost((activity.resources.displayMetrics.widthPixels * 0.9f).toInt()),
                WindowManager.LayoutParams.WRAP_CONTENT)
            // keep the game's full-screen mode while the dialog is up
            setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        dialog = d
        d.show()
        d.window?.apply {
            decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

}
