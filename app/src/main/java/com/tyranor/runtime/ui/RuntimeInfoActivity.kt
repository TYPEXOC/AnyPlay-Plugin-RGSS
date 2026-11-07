package com.tyranor.runtime.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hatkid.mkxpz.BuildConfig
import com.hatkid.mkxpz.R
import com.tyranor.runtime.diagnostics.TynRuntimeDiagnostics

/** Launcher entry used to identify the runtime package; games must be started by the host app. */
class RuntimeInfoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(ImageView(this).apply {
            setImageDrawable(applicationInfo.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(112), dp(112)))
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 25f
            setTextColor(Color.rgb(112, 112, 112))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = getString(R.string.runtime_info_version, BuildConfig.VERSION_NAME)
            textSize = 16f
            setTextColor(Color.rgb(150, 150, 150))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(28))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = getString(R.string.runtime_info_message)
            textSize = 16f
            setTextColor(Color.rgb(72, 132, 212))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(232, 241, 255))
                cornerRadius = dp(12).toFloat()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = getString(R.string.runtime_info_export_diagnostics)
            textSize = 16f
            setTextColor(Color.rgb(72, 132, 212))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(240, 246, 255))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                try {
                    val report = TynRuntimeDiagnostics.createReport(this@RuntimeInfoActivity, null)
                    if (report == null) {
                        android.widget.Toast.makeText(
                            this@RuntimeInfoActivity,
                            R.string.runtime_diagnostics_no_logs,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        TynRuntimeDiagnostics.share(
                            this@RuntimeInfoActivity,
                            report,
                            getString(R.string.runtime_diagnostics_share_title),
                        )
                    }
                } catch (error: Exception) {
                    android.widget.Toast.makeText(
                        this@RuntimeInfoActivity,
                        R.string.runtime_diagnostics_export_failed,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 2f))
        root.addView(TextView(this).apply {
            text = getString(R.string.runtime_info_source)
            textSize = 14f
            setTextColor(Color.rgb(125, 125, 125))
            gravity = Gravity.CENTER
            autoLinkMask = Linkify.WEB_URLS
            linksClickable = true
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, dp(12), 0, dp(8))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}
