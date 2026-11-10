package com.tyranor.runtime.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tyranor.runtime.rgss.BuildConfig
import com.hatkid.mkxpz.MainActivity
import com.tyranor.runtime.rgss.R
import com.tyranor.runtime.diagnostics.TynRuntimeDiagnostics
import java.io.File

/** Runtime package info page with a direct game launcher in diagnostic builds. */
class RuntimeInfoActivity : Activity() {
    private var pendingFolderPickerAfterPermission = false

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
            text = getString(
                if (BuildConfig.DEBUG) R.string.runtime_info_debug_message else R.string.runtime_info_message,
            )
            textSize = 16f
            setTextColor(Color.rgb(72, 132, 212))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(232, 241, 255))
                cornerRadius = dp(12).toFloat()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (BuildConfig.DEBUG) {
            root.addView(TextView(this).apply {
                text = getString(R.string.runtime_info_diagnostic_launch)
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(72, 132, 212))
                    cornerRadius = dp(12).toFloat()
                }
                setOnClickListener { ensureStorageAccessThenPickFolder() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            })
        }
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
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                val button = this
                val defaultText = text
                text = getString(R.string.runtime_info_exporting_diagnostics)
                Thread({
                    var archive: java.io.File? = null
                    var failure: Exception? = null
                    try {
                        archive = TynRuntimeDiagnostics.createArchive(this@RuntimeInfoActivity)
                    } catch (error: Exception) {
                        failure = error
                    }
                    val result = archive
                    val exportFailure = failure
                    runOnUiThread {
                        button.isEnabled = true
                        button.text = defaultText
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (exportFailure != null) {
                            android.widget.Toast.makeText(
                                this@RuntimeInfoActivity,
                                R.string.runtime_diagnostics_export_failed,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else if (result == null) {
                            android.widget.Toast.makeText(
                                this@RuntimeInfoActivity,
                                R.string.runtime_diagnostics_no_logs,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            try {
                                TynRuntimeDiagnostics.share(
                                    this@RuntimeInfoActivity,
                                    result,
                                    getString(R.string.runtime_diagnostics_share_title),
                                )
                            } catch (error: Exception) {
                                android.widget.Toast.makeText(
                                    this@RuntimeInfoActivity,
                                    R.string.runtime_diagnostics_export_failed,
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }, "tyn-rgss-diagnostics-export").start()
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

    private fun ensureStorageAccessThenPickFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            pendingFolderPickerAfterPermission = true
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")),
                    REQUEST_STORAGE_PERMISSION,
                )
            } catch (_: Exception) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_STORAGE_PERMISSION)
            }
            return
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.R &&
            (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            pendingFolderPickerAfterPermission = true
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION,
            )
            return
        }
        openGameFolderPicker()
    }

    private fun openGameFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_GAME_FOLDER)
    }

    @Deprecated("The diagnostic launcher uses the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            val shouldPick = pendingFolderPickerAfterPermission
            pendingFolderPickerAfterPermission = false
            if (granted && shouldPick) openGameFolderPicker()
            else if (!granted) Toast.makeText(this, R.string.runtime_info_storage_permission_required, Toast.LENGTH_LONG).show()
            return
        }
        if (requestCode != REQUEST_GAME_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val gameFolder = resolveFilesystemPath(uri)
        if (gameFolder == null || !looksLikeRgssGame(gameFolder)) {
            Toast.makeText(this, R.string.runtime_info_invalid_game_folder, Toast.LENGTH_LONG).show()
            return
        }
        chooseRubyRuntime(gameFolder)
    }

    @Deprecated("Required for Android versions that use runtime storage permissions")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_STORAGE_PERMISSION) return
        val shouldPick = pendingFolderPickerAfterPermission
        pendingFolderPickerAfterPermission = false
        if (shouldPick && grantResults.size >= 2 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            openGameFolderPicker()
        } else if (shouldPick) {
            Toast.makeText(this, R.string.runtime_info_storage_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveFilesystemPath(treeUri: Uri): File? {
        return try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val separator = documentId.indexOf(':')
            if (separator <= 0) return null
            val volumeId = documentId.substring(0, separator)
            val relativePath = Uri.decode(documentId.substring(separator + 1)).trimStart('/')
            val root = if (volumeId.equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory()
            } else {
                File("/storage", volumeId).takeIf { it.isDirectory }
            } ?: return null
            val resolved = File(root, relativePath).canonicalFile
            val canonicalRoot = root.canonicalFile
            if (resolved.path != canonicalRoot.path &&
                !resolved.path.startsWith(canonicalRoot.path + File.separator)) return null
            resolved
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeRgssGame(folder: File): Boolean {
        if (!folder.isDirectory) return false
        return listOf(
            "Game.ini", "Game.rgssad", "Game.rgss2a", "Game.rgss3a",
            "Data/Actors.rxdata", "Data/Actors.rvdata", "Data/Actors.rvdata2",
        ).any { File(folder, it).isFile }
    }

    private fun chooseRubyRuntime(gameFolder: File) {
        val choices = resources.getStringArray(R.array.runtime_diagnostic_ruby_choices)
        val values = arrayOf("auto", "187", "193", "313")
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.runtime_info_select_ruby)
            .setItems(choices) { _, index -> launchDiagnosticGame(gameFolder, values[index]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchDiagnosticGame(gameFolder: File, rubyRuntime: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DIAGNOSTIC_GAME_DIR, gameFolder.absolutePath)
            putExtra(MainActivity.EXTRA_STANDALONE_DIAGNOSTIC, true)
            putExtra(MainActivity.EXTRA_DIAGNOSTIC_RUBY_RUNTIME, rubyRuntime)
            putExtra(MainActivity.EXTRA_DIAGNOSTIC_GAME_TITLE, gameFolder.name)
        }
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 4201
        private const val REQUEST_GAME_FOLDER = 4202
    }
}
