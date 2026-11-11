package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.storage.StorageManager;
import android.os.storage.OnObbStateChangeListener;
import android.net.Uri;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.widget.Toast;
import java.util.Locale;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.libsdl.app.SDLActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import com.tyranor.runtime.diagnostics.TynRuntimeDiagnostics;
import com.tyranor.runtime.ui.TynRuntimeUi;
import com.tyranor.runtime.rgss.BuildConfig;
import com.tyranor.runtime.rgss.R;

public class MainActivity extends SDLActivity
{
    // This activity inherits from SDLActivity activity.
    // Put your Java-side stuff here.

    private static final String TAG = "mkxp-z[Activity]";
    private static final String GAME_PATH_DEFAULT = Environment.getExternalStorageDirectory() + "/mkxp-z";
    private static String GAME_PATH = GAME_PATH_DEFAULT;
    private static String GAME_FOLDER = GAME_PATH_DEFAULT;
    private static String PAD_PROFILE_PATH;
    private static String OBB_MAIN_FILENAME;
    private static boolean DEBUG = false;

    // ---- Runtime module launch ------------------------------------------
    // TYN supplies the v1 launch payload plus optional TYN-specific extensions.
    public static final String EXTRA_DIAGNOSTIC_GAME_DIR = "com.tyranor.runtime.extra.DIAGNOSTIC_GAME_DIR";
    public static final String EXTRA_STANDALONE_DIAGNOSTIC = "com.tyranor.runtime.extra.STANDALONE_DIAGNOSTIC";
    public static final String EXTRA_DIAGNOSTIC_RUBY_RUNTIME = "com.tyranor.runtime.extra.DIAGNOSTIC_RUBY_RUNTIME";
    public static final String EXTRA_DIAGNOSTIC_GAME_TITLE = "com.tyranor.runtime.extra.DIAGNOSTIC_GAME_TITLE";
    private static final String TYN_ACTION_LAUNCH = "com.tyranor.runtime.action.LAUNCH";
    private static final String TYN_EXTRA_PROTOCOL_VERSION = "com.tyranor.runtime.extra.PROTOCOL_VERSION";
    private static final String TYN_EXTRA_FOLDER = "com.tyranor.runtime.extra.FOLDER_PATH";
    private static final String TYN_EXTRA_TITLE = "com.tyranor.runtime.extra.GAME_TITLE";
    private static final String TYN_EXTRA_GAME_ID = "com.tyranor.runtime.extra.GAME_ID";
    private static final String TYN_EXTRA_TRANSLATION_MAP_URI = "com.tyranor.runtime.extra.TRANSLATION_MAP_URI";
    private static final String TYN_EXTRA_PAD_ENABLED = "com.tyranor.runtime.extra.PAD_ENABLED";
    private static final String TYN_EXTRA_PAD_PROFILE_URI = "com.tyranor.runtime.extra.PAD_PROFILE_URI";
    private static final String TYN_EXTRA_PAD_PROFILE_ID = "com.tyranor.runtime.extra.PAD_PROFILE_ID";
    private static final String TYN_EXTRA_RUBY_RUNTIME = "com.tyranor.runtime.extra.RUBY_RUNTIME";
    private static final String TYN_EXTRA_RGSS_OPTIONS = "com.tyranor.runtime.extra.RGSS_OPTIONS";
    private static final String TYN_EXTRA_RTP_ROOT = "com.tyranor.runtime.extra.RTP_ROOT";
    private static final String TYN_EXTRA_THEME_PRIMARY_COLOR = "com.tyranor.runtime.extra.THEME_PRIMARY_COLOR";
    private static final String TYN_EXTRA_THEME_ON_PRIMARY_COLOR = "com.tyranor.runtime.extra.THEME_ON_PRIMARY_COLOR";
    private static final String TYN_EXTRA_SAVE_SESSION_ENDED_CALLBACK = "com.tyranor.runtime.extra.SAVE_SESSION_ENDED_CALLBACK";
    private static final String[] RUBY_187_LIBRARIES = {
        "SDL2", "SDL2_image", "SDL2_ttf", "SDL2_sound", "openal", "fluidsynth", "ruby187", "mkxp-z-187"
    };
    private static final String[] RUBY_193_LIBRARIES = {
        "SDL2", "SDL2_image", "SDL2_ttf", "SDL2_sound", "openal", "fluidsynth", "ruby193", "mkxp-z-193"
    };
    private static final String[] RUBY_313_LIBRARIES = {
        "SDL2", "SDL2_image", "SDL2_ttf", "SDL2_sound", "openal", "fluidsynth", "ruby", "mkxp-z"
    };
    private static int sRubyRuntime = 313;
    private boolean mCustomGameDir = false;
    private boolean mTynLaunch = false;
    private boolean mInvalidLaunchRequest = false;
    private boolean mPadEnabled = true;
    private boolean mCheatsEnabled = true;
    private boolean mDebugEnabled = false;
    private boolean mCopyTextEnabled = false;
    private int mSpeedUp = 1;
    private int mThemePrimaryColor = 0xFF18B978;
    private int mThemeOnPrimaryColor = 0xFFFFFFFF;
    private String mPadProfileUri;
    private String mTranslationMapUri;
    private String mGameId;
    private String mTynRtpRoot;
    private JSONObject mTynRgssOptions;
    private File mSessionDir;
    private boolean mWaitingForStoragePermission;
    private boolean mRtpScanNeedsRefresh;
    private String mTynGameTitle;
    private PendingIntent mTynSaveSessionEndedCallback;
    private boolean mTynSaveSessionEndedCallbackSent;
    private ScheduledExecutorService mTranslationSync;
    private volatile long mLastTranslationFailureLogAt;
    private boolean mDiagnosticsExporting;
    private long mLaunchRequestedAtMillis;
    private long mLaunchRequestedAtElapsedRealtime;

    protected boolean mStarted = false;

    private StorageManager mStorageManager;

    // TYN virtual pad, cheat panel, loading indicator, and close-game dialog
    private TynRuntimeUi mUi;
    private static MainActivity sInstance;

    private void runSDLThread()
    {
        boolean wasStarted = mStarted;
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
        }

        TynRuntimeDiagnostics.append(mSessionDir,
            wasStarted ? "Native SDL runtime resume requested" : "Native SDL runtime start requested");
        mStarted = true;

        // Run (resume) native SDL thread
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    OnObbStateChangeListener obbListener = new OnObbStateChangeListener()
    {
        @Override
        public void onObbStateChange(String path, int state)
        {
            super.onObbStateChange(path, state);

            Log.v(TAG, "OBB state of " + path + " changed to " + state);

            switch (state)
            {
                case OnObbStateChangeListener.MOUNTED:
                    String obbPath = mStorageManager.getMountedObbPath(path);
                    Log.v(TAG, "OBB " + path + " is mounted to " + obbPath);
                    GAME_PATH = obbPath;
                    break;

                case OnObbStateChangeListener.UNMOUNTED:
                    Log.v(TAG, "OBB " + path + " is unmounted");
                    GAME_PATH = GAME_PATH_DEFAULT;
                    break;

                default:
                    Log.e(TAG, "Failed to mount OBB " + path + ": Got state " + state);
                    break;
            }

            runSDLThread();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 110) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                // Close the App because the User did not allow the all files access permission to be used.
                finish();
                return;
            }
            if (mTynLaunch) {
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Storage access granted; rebuilding the private runtime session before native startup");
                preparePrivateSession(mTynGameTitle);
            } else if (mRtpScanNeedsRefresh) {
                refreshTynRtpPathsAfterPermission();
            }
            mWaitingForStoragePermission = false;
            TynRuntimeDiagnostics.append(mSessionDir,
                "All-files access granted; continuing with refreshed runtime paths");
            startRuntimeWhenReady();
        }
    }

    /** Re-scan the TYN RTP root after first-time all-files access is granted. */
    private void refreshTynRtpPathsAfterPermission()
    {
        if (!mTynLaunch || mSessionDir == null) return;
        File configFile = new File(mSessionDir, "mkxp.json");
        try {
            byte[] bytes = readBytes(configFile, 1024 * 1024);
            JSONObject config = new JSONObject(new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "").trim());
            TynRuntimeDiagnostics.append(mSessionDir,
                "Refreshing RTP paths after storage permission; rescanning the configured TYN RTP root");
            configureRtpPaths(config);
            writeAtomically(configFile, config.toString());
            mRtpScanNeedsRefresh = false;
            TynRuntimeDiagnostics.append(mSessionDir, "RTP paths refreshed before native runtime startup");
        } catch (Exception error) {
            TynRuntimeDiagnostics.append(mSessionDir, "Could not refresh RTP paths after storage permission", error);
        }
    }

    /**
     * Validates the module contract before SDL can start. The native side reads
     * GAME_PATH when its thread starts, so the path must be set before onCreate.
     */
    private void applyGameDirFromIntent(Intent intent)
    {
        if (intent == null) {
            rejectLaunch("Rejected activity launch without an Intent");
            return;
        }

        if (intent.getBooleanExtra(EXTRA_STANDALONE_DIAGNOSTIC, false)) {
            mLaunchRequestedAtMillis = System.currentTimeMillis();
            mLaunchRequestedAtElapsedRealtime = SystemClock.elapsedRealtime();
            String dir = intent.getStringExtra(EXTRA_DIAGNOSTIC_GAME_DIR);
            if (!isAbsoluteDirectory(dir)) {
                rejectLaunch("Standalone diagnostic launch has no absolute game folder");
                return;
            }
            GAME_FOLDER = new File(dir).getAbsolutePath();
            GAME_PATH = GAME_FOLDER;
            sRubyRuntime = resolveRubyRuntime(
                intent.getStringExtra(EXTRA_DIAGNOSTIC_RUBY_RUNTIME), GAME_FOLDER);
            mCustomGameDir = true;
            mPadEnabled = true;
            mCheatsEnabled = false;
            mDebugEnabled = false;
            mCopyTextEnabled = false;
            mSpeedUp = 1;
            prepareStandaloneDiagnosticSession(intent.getStringExtra(EXTRA_DIAGNOSTIC_GAME_TITLE));
            Log.i(TAG, "Standalone diagnostic game folder: " + GAME_FOLDER + ", session: " + GAME_PATH);
            return;
        }

        if (TYN_ACTION_LAUNCH.equals(intent.getAction())) {
            mTynLaunch = true;
            mLaunchRequestedAtMillis = System.currentTimeMillis();
            mLaunchRequestedAtElapsedRealtime = SystemClock.elapsedRealtime();
            // Protocol v1 is the default in the module contract; explicit unknown versions are rejected.
            int protocol = intent.getIntExtra(TYN_EXTRA_PROTOCOL_VERSION, 1);
            if (protocol != 1) {
                rejectLaunch("Unsupported or missing runtime-module protocol version: " + protocol);
                return;
            }
            Log.i(TAG, "TYN runtime-module launch accepted; protocol=" + protocol);
            String rubyRuntime = intent.getStringExtra(TYN_EXTRA_RUBY_RUNTIME);
            String dir = intent.getStringExtra(TYN_EXTRA_FOLDER);
            if (!isAbsoluteDirectory(dir)) {
                rejectLaunch("TYN runtime launch requires an absolute game folder");
                return;
            }
            GAME_FOLDER = new File(dir).getAbsolutePath();
            GAME_PATH = GAME_FOLDER;
            sRubyRuntime = resolveRubyRuntime(rubyRuntime, GAME_FOLDER);
            mCustomGameDir = true;
            mPadEnabled = intent.getBooleanExtra(TYN_EXTRA_PAD_ENABLED, true);
            try {
                String options = intent.getStringExtra(TYN_EXTRA_RGSS_OPTIONS);
                mTynRgssOptions = options == null ? null : new JSONObject(options);
                if (mTynRgssOptions != null) {
                    mCheatsEnabled = mTynRgssOptions.optBoolean("cheatsEnabled", true);
                    mDebugEnabled = mTynRgssOptions.optBoolean("debug", false);
                    mCopyTextEnabled = mTynRgssOptions.optBoolean("copyText", false);
                    mSpeedUp = Math.max(1, Math.min(9, mTynRgssOptions.optInt("speedUp", 1)));
                }
            } catch (Exception error) {
                Log.w(TAG, "Ignoring invalid TYN RGSS options", error);
                mTynRgssOptions = null;
            }
            mPadProfileUri = intent.getStringExtra(TYN_EXTRA_PAD_PROFILE_URI);
            mTynRtpRoot = intent.getStringExtra(TYN_EXTRA_RTP_ROOT);
            mThemePrimaryColor = intent.getIntExtra(TYN_EXTRA_THEME_PRIMARY_COLOR, mThemePrimaryColor);
            mThemeOnPrimaryColor = intent.getIntExtra(TYN_EXTRA_THEME_ON_PRIMARY_COLOR, mThemeOnPrimaryColor);
            mTranslationMapUri = intent.getStringExtra(TYN_EXTRA_TRANSLATION_MAP_URI);
            mGameId = intent.getStringExtra(TYN_EXTRA_GAME_ID);
            mTynGameTitle = intent.getStringExtra(TYN_EXTRA_TITLE);
            mTynSaveSessionEndedCallback = intent.getParcelableExtra(TYN_EXTRA_SAVE_SESSION_ENDED_CALLBACK);
            preparePrivateSession(mTynGameTitle);
            Log.i(TAG, "TYN runtime game folder: " + GAME_FOLDER + ", session: " + GAME_PATH);
            return;
        }

        rejectLaunch("Rejected activity launch outside the TYN module contract");
    }

    private static boolean isAbsoluteDirectory(String path)
    {
        if (path == null || path.trim().isEmpty()) return false;
        File folder = new File(path);
        return folder.isAbsolute() && folder.isDirectory();
    }

    private void rejectLaunch(String reason)
    {
        mInvalidLaunchRequest = true;
        Log.e(TAG, reason);
    }

    @Override
    protected String[] getLibraries()
    {
        if (sRubyRuntime == 187) return RUBY_187_LIBRARIES;
        if (sRubyRuntime == 193) return RUBY_193_LIBRARIES;
        return RUBY_313_LIBRARIES;
    }

    /** Creates the private mkxp session; the original game folder is only the configured gameFolder. */
    private void preparePrivateSession(String title)
    {
        try {
            boolean initializeDiagnostics = mSessionDir == null;
            if (initializeDiagnostics) {
                String suffix = shortHash((mGameId == null ? GAME_FOLDER : mGameId) + System.nanoTime());
                mSessionDir = new File(getFilesDir(), "tyranor/sessions/" + suffix);
            }
            if (!mSessionDir.isDirectory() && !mSessionDir.mkdirs()) throw new java.io.IOException("Unable to create session folder");
            writeAtomically(new File(mSessionDir, "fast_forward"), "0");
            writeAtomically(new File(mSessionDir, "tyranor_copy_text.txt"), "");
            if (initializeDiagnostics) {
                TynRuntimeDiagnostics.beginSession(
                    this,
                    mSessionDir,
                    title,
                    new File(GAME_FOLDER),
                    sRubyRuntime,
                    detectRgssVersion(GAME_FOLDER),
                    mTranslationMapUri != null,
                    mPadEnabled,
                    mTynRgssOptions == null ? "{}" : mTynRgssOptions.toString(),
                    mLaunchRequestedAtMillis,
                    mLaunchRequestedAtElapsedRealtime
                );
            } else {
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Re-reading game files and rebuilding the private mkxp configuration after storage access was granted");
            }
            try {
                Os.setenv("TYN_RGSS_MISSING_RESOURCE_HINT", getString(R.string.tyn_runtime_missing_resource_hint), true);
                Os.setenv("TYN_RGSS_MANAGED_RTP", "1", true);
                boolean verboseDiagnostics = BuildConfig.TYN_VERBOSE_DIAGNOSTICS || mDebugEnabled;
                TynRuntimeDiagnostics.setVerboseInputDiagnostics(verboseDiagnostics);
                Os.setenv("TYN_RGSS_VERBOSE_LOG", verboseDiagnostics ? "1" : "0", true);
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Verbose runtime diagnostics enabled=" + verboseDiagnostics
                        + " (diagnostic build=" + BuildConfig.TYN_VERBOSE_DIAGNOSTICS + ")");
            } catch (Exception error) {
                TynRuntimeDiagnostics.append(mSessionDir, "Could not set TYN runtime environment flags", error);
            }
            TynRuntimeDiagnostics.append(mSessionDir, "Runtime setup: copying TYN Ruby translation bridge");

            File mkxpCompat = new File(mSessionDir, "tyranor_mkxp_compat.rb");
            File win32Compat = new File(mSessionDir, "tyranor_win32_compat.rb");
            File inputCompat = new File(mSessionDir, "tyranor_input_compat.rb");
            File nativeCheatWindow = mCheatsEnabled
                ? new File(mSessionDir, "tyranor_rgss_native_cheats.rb") : null;
            copyAssetToFile("tyranor_mkxp_compat.rb", mkxpCompat);
            copyAssetToFile("tyranor_win32_compat.rb", win32Compat);
            copyAssetToFile("tyranor_input_compat.rb", inputCompat);
            if (nativeCheatWindow != null) {
                copyAssetToFile("tyranor_rgss_native_cheats.rb", nativeCheatWindow);
            }
            TynRuntimeDiagnostics.append(mSessionDir,
                "Runtime setup: copied MKXP, Win32API, and input-update compatibility preloads");

            File rubyConfigPreload = prepareBundledRubyConfigPreload();
            File discordCompatPreload = prepareWindowsDiscordCompatPreload();

            File bridge = new File(mSessionDir, "tyranor_rgss_translation.rb");
            copyAssetToFile("tyranor_rgss_translation.rb", bridge);
            writeAtomically(new File(mSessionDir, "rgss_version"), String.valueOf(detectRgssVersion(GAME_FOLDER)));

            File profile = new File(mSessionDir, "tyranor_pad_profile.json");
            PAD_PROFILE_PATH = profile.getAbsolutePath();
            if (mPadProfileUri != null) {
                TynRuntimeDiagnostics.append(mSessionDir, "Runtime setup: importing virtual-pad profile");
                boolean copied = copyUriToFile(mPadProfileUri, profile, 1024 * 1024);
                TynRuntimeDiagnostics.append(mSessionDir,
                    copied ? "Virtual-pad profile imported" : "Virtual-pad profile import failed");
            }

            if (mTranslationMapUri != null) {
                TynRuntimeDiagnostics.append(mSessionDir, "Runtime setup: importing selected translation table");
                File table = new File(mSessionDir, "runtime_translations.hex");
                if (!syncTranslationTable(mTranslationMapUri, table)) {
                    TynRuntimeDiagnostics.append(mSessionDir, "Selected translation table could not be loaded; continuing without it");
                    mTranslationMapUri = null;
                }
            }

            TynRuntimeDiagnostics.append(mSessionDir, "Runtime setup: checking optional game-folder mkxp.json");
            JSONObject config = readGameConfig();
            config.put("gameFolder", GAME_FOLDER);
            config.put("rgssVersion", detectRgssVersion(GAME_FOLDER));
            if (title != null && !title.trim().isEmpty()) config.put("windowTitle", title);
            applyTynRgssOptions(config);
            configureMidiSoundFont(config);
            JSONArray gameScripts = stringArray(config.opt("preloadScript"));
            JSONArray scripts = new JSONArray();
            if (rubyConfigPreload != null) scripts.put(rubyConfigPreload.getAbsolutePath());
            if (discordCompatPreload != null) scripts.put(discordCompatPreload.getAbsolutePath());
            scripts.put(mkxpCompat.getAbsolutePath());
            scripts.put(win32Compat.getAbsolutePath());
            scripts.put(inputCompat.getAbsolutePath());
            for (int i = 0; i < gameScripts.length(); i++) {
                String path = gameScripts.optString(i, "");
                if (!path.isEmpty() && !containsString(scripts, path)) scripts.put(path);
            }
            appendGameRootScript(scripts, "preload.rb", "preload");
            String bridgePath = bridge.getAbsolutePath();
            if (!containsString(scripts, bridgePath)) scripts.put(bridgePath);
            config.put("preloadScript", scripts);
            JSONArray postloadScripts = stringArray(config.opt("postloadScript"));
            if (isPostloadScriptsEnabled()) {
                appendGameRootScript(postloadScripts, "postload.rb", "postload");
            }
            if (nativeCheatWindow != null && !containsString(postloadScripts, nativeCheatWindow.getAbsolutePath())) {
                postloadScripts.put(nativeCheatWindow.getAbsolutePath());
                TynRuntimeDiagnostics.append(mSessionDir,
                    "RGSS native cheat window registered as an internal postload script");
            }
            config.put("postloadScript", postloadScripts);
            writeAtomically(new File(mSessionDir, "mkxp.json"), config.toString());
            GAME_PATH = mSessionDir.getAbsolutePath();
            TynRuntimeDiagnostics.append(mSessionDir, "Runtime setup complete; private mkxp configuration is ready");
        } catch (Exception error) {
            Log.e(TAG, "Could not prepare private TYN session; falling back to the game folder", error);
            TynRuntimeDiagnostics.append(mSessionDir, "Private session preparation failed", error);
            GAME_PATH = GAME_FOLDER;
        }
    }

    private JSONObject readStandaloneGameConfig()
    {
        File source = new File(GAME_FOLDER, "mkxp.json");
        if (source.isFile() && source.length() <= 1024 * 1024) {
            try {
                byte[] bytes = readBytes(source, 1024 * 1024);
                String text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Optional game-folder mkxp.json found; preserving its engine settings");
                return new JSONObject(text);
            } catch (Exception error) {
                Log.w(TAG, "Ignoring invalid game mkxp.json in standalone diagnostic", error);
                TynRuntimeDiagnostics.append(mSessionDir, "Could not read optional game mkxp.json", error);
            }
        }
        TynRuntimeDiagnostics.append(mSessionDir,
            "No game-folder mkxp.json found; continuing with native engine defaults (the file is optional)");
        return new JSONObject();
    }

    /** Creates an isolated mkxp config for direct engine diagnosis without TYN preload scripts. */
    private void prepareStandaloneDiagnosticSession(String title)
    {
        try {
            String suffix = shortHash(GAME_FOLDER + System.nanoTime());
            mSessionDir = new File(getFilesDir(), "tyranor/sessions/diagnostic-" + suffix);
            if (!mSessionDir.isDirectory() && !mSessionDir.mkdirs()) {
                throw new java.io.IOException("Unable to create diagnostic session folder");
            }
            writeAtomically(new File(mSessionDir, "fast_forward"), "0");
            writeAtomically(new File(mSessionDir, "tyranor_copy_text.txt"), "");
            int rgssVersion = detectRgssVersion(GAME_FOLDER);
            TynRuntimeDiagnostics.beginSession(
                this,
                mSessionDir,
                title,
                new File(GAME_FOLDER),
                sRubyRuntime,
                rgssVersion,
                false,
                true,
                "{\"launchMode\":\"standalone-diagnostic\"}",
                mLaunchRequestedAtMillis,
                mLaunchRequestedAtElapsedRealtime
            );
            TynRuntimeDiagnostics.append(mSessionDir,
                "Standalone launch target: " + GAME_FOLDER + "; detected RGSS generation=" + rgssVersion
                    + "; selected Ruby=" + sRubyRuntime);
            try {
                Os.setenv("TYN_RGSS_MISSING_RESOURCE_HINT", getString(R.string.tyn_runtime_missing_resource_hint), true);
                Os.setenv("TYN_RGSS_MANAGED_RTP", "0", true);
                Os.setenv("TYN_RGSS_VERBOSE_LOG", "1", true);
                TynRuntimeDiagnostics.setVerboseInputDiagnostics(true);
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Standalone diagnostic mode: verbose logging on; no TYN Ruby preloads or UI integrations");
            } catch (Exception error) {
                TynRuntimeDiagnostics.append(mSessionDir, "Could not set standalone diagnostic environment flags", error);
            }

            JSONObject config = readStandaloneGameConfig();
            config.put("gameFolder", GAME_FOLDER);
            config.put("rgssVersion", rgssVersion);
            if (title != null && !title.trim().isEmpty()) config.put("windowTitle", title);
            configureMidiSoundFont(config);
            writeAtomically(new File(mSessionDir, "mkxp.json"), config.toString());
            GAME_PATH = mSessionDir.getAbsolutePath();
            TynRuntimeDiagnostics.append(mSessionDir,
                "Standalone diagnostic config ready; game scripts will be loaded without TYN compatibility preloads");
        } catch (Exception error) {
            Log.e(TAG, "Could not prepare standalone diagnostic session", error);
            TynRuntimeDiagnostics.append(mSessionDir, "Standalone diagnostic session preparation failed", error);
            GAME_PATH = GAME_FOLDER;
        }
    }

    private void copyAssetToFile(String assetName, File destination) throws java.io.IOException
    {
        try (InputStream input = getAssets().open(assetName); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
    }

    private void configureMidiSoundFont(JSONObject config) throws Exception
    {
        Object configuredValue = config.opt("midiSoundFont");
        String configuredPath = configuredValue instanceof String ? ((String) configuredValue).trim() : "";
        if (!configuredPath.isEmpty()) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "MIDI setup: preserving configured SoundFont path " + configuredPath);
            return;
        }

        File soundFontDir = new File(getFilesDir(), "tyranor/soundfonts");
        if (!soundFontDir.isDirectory() && !soundFontDir.mkdirs()) {
            throw new java.io.IOException("Unable to create private MIDI SoundFont folder");
        }
        File soundFont = new File(soundFontDir, "GMGSx-614ffa8e.SF2");
        if (!soundFont.isFile() || soundFont.length() != 4_146_440L) {
            copyAssetToFile("GMGSx.SF2", soundFont);
        }
        if (!soundFont.isFile() || soundFont.length() != 4_146_440L) {
            throw new java.io.IOException("Bundled GMGSx SoundFont has an unexpected size");
        }
        config.put("midiSoundFont", soundFont.getAbsolutePath());
        TynRuntimeDiagnostics.append(mSessionDir,
            "MIDI setup: configured bundled GMGSx SoundFont from the module's private files at "
                + soundFont.getAbsolutePath()
                + " (" + soundFont.length() + " bytes)");
    }

    /** Applies TYN RPG Maker settings to the private mkxp session, never to the game folder. */
    private void applyTynRgssOptions(JSONObject config) throws Exception
    {
        JSONObject options = mTynRgssOptions;
        if (options != null) {
            String[] booleanKeys = {
                "debug", "smoothScaling", "vsync", "frameSkip", "solidFonts", "pathCache",
                "prebuiltPathCache", "fastPathEnum", "copyText", "useCJKFont", "animateAutotiles"
            };
            for (String key : booleanKeys) {
                if (options.has(key)) {
                    String configKey = "debug".equals(key) ? "debugMode" : key;
                    config.put(configKey, options.optBoolean(key));
                }
            }

            config.put("fontScale", parseFloat(options.optString("fontScale", "0.75"), 0.75f));
            config.put("customFont", options.optString("customFont", ""));
            config.put("verticalScreenAlign", options.optString("verticalScreenAlign", "top-center"));
            config.put("speedUp", mSpeedUp);

            // JoiPlay's postload switch controlled whether configured postload scripts ran.
            // Keep that behavior, with execution implemented by this module's native Ruby host.
            if (options.optBoolean("enablePostloadScripts", false)) {
                config.put("postloadScript", stringArray(config.opt("postloadScript")));
            } else {
                config.put("postloadScript", new JSONArray());
            }
        }

        configureRtpPaths(config);

        if (options == null) return;
        String screenSize = options.optString("screenSize", "auto");
        if ("auto".equals(screenSize)) return;
        String[] supportedSizes = {
            "512x384", "512x768", "544x416", "640x480", "800x600",
            "1024x768", "1280x720", "1280x960", "1920x1080"
        };
        for (String supported : supportedSizes) {
            if (supported.equals(screenSize)) {
                String[] dimensions = supported.split("x");
                config.put("defScreenW", Integer.parseInt(dimensions[0]));
                config.put("defScreenH", Integer.parseInt(dimensions[1]));
                break;
            }
        }
    }

    /** Uses the RTP directory supplied by TYN and rejects stale paths from other hosts. */
    private void configureRtpPaths(JSONObject config) throws JSONException
    {
        mRtpScanNeedsRefresh = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && !Environment.isExternalStorageManager();
        int rgssVersion = detectRgssVersion(GAME_FOLDER);
        String versionFolder = rgssVersion == 1 ? "RPGXP" : rgssVersion == 2 ? "RPGVX" : "RPGVXACE";
        JSONArray rtps = new JSONArray();
        JSONArray configured = stringArray(config.opt("RTP"));
        File rtpRoot = mTynRtpRoot == null || mTynRtpRoot.trim().isEmpty()
            ? null
            : new File(mTynRtpRoot);
        File gameRoot = new File(GAME_FOLDER);
        int skippedForeign = 0;
        int skippedMissing = 0;
        for (int i = 0; i < configured.length(); i++) {
            String entry = configured.optString(i, "").trim();
            if (entry.isEmpty()) continue;
            File path = new File(entry);
            if (!path.isAbsolute()) path = new File(GAME_FOLDER, entry);
            if (!isInsideRoot(gameRoot, path) && (rtpRoot == null || !isInsideRoot(rtpRoot, path))) {
                skippedForeign++;
                continue;
            }
            if (!path.isDirectory()) {
                skippedMissing++;
                continue;
            }
            String resolved = path.getAbsolutePath();
            if (!containsString(rtps, resolved)) rtps.put(resolved);
        }

        File versionRtp = rtpRoot == null
            ? null
            : new File(new File(rtpRoot, versionFolder), "app");
        boolean tynRtpAvailable = versionRtp != null && hasRtpFiles(versionRtp);
        if (tynRtpAvailable) {
            String resolved = versionRtp.getAbsolutePath();
            if (!containsString(rtps, resolved)) rtps.put(resolved);
            TynRuntimeDiagnostics.append(mSessionDir, "RTP source: TYN-managed " + versionFolder + " resources available");
        } else {
            TynRuntimeDiagnostics.append(mSessionDir, "RTP source: no imported TYN " + versionFolder + " resources found");
        }
        if (skippedForeign > 0) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ignored " + skippedForeign + " RTP path(s) outside the game folder and TYN-managed root");
        }
        if (skippedMissing > 0) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ignored " + skippedMissing + " unavailable RTP path(s) from runtime configuration");
        }
        if (skippedForeign > 0 && !tynRtpAvailable) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "The game references an external RTP path, but no matching RTP is imported in TYN settings");
        }
        config.put("RTP", rtps);
    }

    private static boolean isInsideRoot(File root, File candidate)
    {
        try {
            String rootPath = root.getCanonicalPath();
            String candidatePath = candidate.getCanonicalPath();
            return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasRtpFiles(File path)
    {
        File[] files = path.listFiles();
        return path.isDirectory() && files != null && files.length > 0;
    }

    private static float parseFloat(String value, float fallback)
    {
        try { return Float.parseFloat(value); } catch (Exception ignored) { return fallback; }
    }

    private static boolean containsString(JSONArray values, String target)
    {
        for (int i = 0; i < values.length(); i++) {
            if (target.equals(values.optString(i))) return true;
        }
        return false;
    }

    private boolean isPostloadScriptsEnabled()
    {
        return mTynRgssOptions == null || mTynRgssOptions.optBoolean("enablePostloadScripts", false);
    }

    private void appendGameRootScript(JSONArray scripts, String fileName, String phase) throws org.json.JSONException
    {
        File script = new File(GAME_FOLDER, fileName);
        if (!script.isFile() || containsFile(scripts, script)) return;
        scripts.put(script.getAbsolutePath());
        TynRuntimeDiagnostics.append(mSessionDir,
            "Detected game-root " + phase + " script: " + script.getAbsolutePath());
    }

    private boolean containsFile(JSONArray paths, File candidate)
    {
        String candidatePath = canonicalPath(candidate);
        for (int i = 0; i < paths.length(); i++) {
            String configuredPath = paths.optString(i, "");
            if (configuredPath.isEmpty()) continue;
            File configured = new File(configuredPath);
            if (!configured.isAbsolute()) configured = new File(GAME_FOLDER, configuredPath);
            if (candidatePath.equals(canonicalPath(configured))) return true;
        }
        return false;
    }

    private static String canonicalPath(File file)
    {
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    /**
     * Windows-packaged MKXP-Z games may bundle Ruby's platform-specific stdlib
     * under stdlib/x64-mingw32 (or another Windows target directory). Their own
     * platform checks do not recognize Android, so add the directory containing
     * rbconfig.rb before any game preload can require it.
     */
    private File prepareBundledRubyConfigPreload()
    {
        if (sRubyRuntime != 313) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ruby stdlib compatibility: skipped because selected runtime is Ruby " + sRubyRuntime);
            return null;
        }

        File stdlibRoot = new File(GAME_FOLDER, "stdlib");
        File[] directories = stdlibRoot.listFiles(File::isDirectory);
        if (directories == null) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ruby stdlib compatibility: no readable game stdlib directory; no rbconfig path added");
            return null;
        }
        Arrays.sort(directories, (left, right) ->
            left.getName().compareToIgnoreCase(right.getName()));

        File selectedDirectory = null;
        for (File directory : directories) {
            String name = directory.getName().toLowerCase(Locale.ROOT);
            boolean windowsTarget = name.contains("mingw") || name.contains("mswin")
                || name.contains("ucrt") || name.contains("windows") || name.contains("win32");
            if (!windowsTarget || !new File(directory, "rbconfig.rb").isFile()) continue;
            if (selectedDirectory == null) selectedDirectory = directory;
            if (name.startsWith("x64-") || name.contains("x86_64")) {
                selectedDirectory = directory;
                break;
            }
        }

        if (selectedDirectory == null) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ruby stdlib compatibility: no Windows-target rbconfig.rb found under " + stdlibRoot.getAbsolutePath());
            return null;
        }

        try {
            File preload = new File(mSessionDir, "tyranor_ruby_stdlib_path.rb");
            String rubyPath = rubySingleQuotedString(selectedDirectory.getAbsolutePath());
            String script = "# Generated by TYN for a bundled Windows Ruby standard library.\n"
                + "tyranor_stdlib_path = " + rubyPath + "\n"
                + "$LOAD_PATH.unshift(tyranor_stdlib_path) unless $LOAD_PATH.include?(tyranor_stdlib_path)\n";
            writeAtomically(preload, script);
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ruby stdlib compatibility: found rbconfig.rb; added its directory before game preloads: "
                    + selectedDirectory.getAbsolutePath());
            return preload;
        } catch (Exception error) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Ruby stdlib compatibility: could not create rbconfig path preload", error);
            return null;
        }
    }

    private static String rubySingleQuotedString(String value)
    {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * A Windows MKXP-Z package can ship a PE Discord extension named discord.so.
     * Android cannot load that binary, while the game's rich-presence script is
     * optional. Install a no-op API only when both that script and PE extension
     * are present, so the unsupported feature cannot abort game startup.
     */
    private File prepareWindowsDiscordCompatPreload()
    {
        File discordExtension = new File(GAME_FOLDER, "gems/discord.so");
        if (!discordExtension.isFile()) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Discord compatibility: no bundled gems/discord.so; no workaround needed");
            return null;
        }

        try {
            if (!isWindowsPeBinary(discordExtension)) {
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Discord compatibility: bundled discord.so is not a Windows PE binary; left unchanged: "
                        + discordExtension.getAbsolutePath());
                return null;
            }

            File preload = new File(mSessionDir, "tyranor_optional_discord_compat.rb");
            String script = "# TYN Android stub for the optional Windows-only Discord Rich Presence extension.\n"
                + "$tyn_android_disable_discord_rpc = true\n"
                + "module Discord\n"
                + "  def self.update; nil; end\n"
                + "  def self.connected?; false; end\n"
                + "  def self.connect(*arguments); nil; end\n"
                + "  def self.disconnect; nil; end\n"
                + "  def self.update_activity(*arguments); nil; end\n"
                + "end\n"
                + "module Kernel\n"
                + "  unless private_method_defined?(:tyn_require_without_discord_rpc)\n"
                + "    alias_method :tyn_require_without_discord_rpc, :require\n"
                + "  end\n"
                + "  def require(feature, *arguments)\n"
                + "    return false if $tyn_android_disable_discord_rpc && feature.to_s == 'discord'\n"
                + "    tyn_require_without_discord_rpc(feature, *arguments)\n"
                + "  end\n"
                + "  private :require\n"
                + "end\n";
            writeAtomically(preload, script);
            TynRuntimeDiagnostics.append(mSessionDir,
                "Discord compatibility: detected Windows PE extension " + discordExtension.getAbsolutePath()
                    + "; disabled optional Discord Rich Presence for Android");
            return preload;
        } catch (Exception error) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Discord compatibility: could not inspect or prepare the optional extension stub", error);
            return null;
        }
    }

    private static boolean isWindowsPeBinary(File file) throws java.io.IOException
    {
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            if (input.length() < 64 || input.readUnsignedShort() != 0x4d5a) return false;
            input.seek(0x3c);
            long peHeaderOffset = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
            if (peHeaderOffset < 64 || peHeaderOffset > input.length() - 4) return false;
            input.seek(peHeaderOffset);
            return input.readUnsignedByte() == 0x50
                && input.readUnsignedByte() == 0x45
                && input.readUnsignedByte() == 0
                && input.readUnsignedByte() == 0;
        }
    }

    private JSONObject readGameConfig()
    {
        File source = new File(GAME_FOLDER, "mkxp.json");
        if (source.isFile() && source.length() <= 1024 * 1024) {
            try {
                byte[] bytes = readBytes(source, 1024 * 1024);
                String text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
                TynRuntimeDiagnostics.append(mSessionDir, "Game-folder mkxp.json found; reading its settings");
                String normalized = stripJsonTrailingCommas(text);
                if (!normalized.equals(text)) {
                    TynRuntimeDiagnostics.append(mSessionDir,
                        "Game-folder mkxp.json contains trailing commas; removed them before parsing");
                }
                return new JSONObject(normalized);
            } catch (Exception error) {
                Log.w(TAG, "Ignoring invalid game mkxp.json", error);
                TynRuntimeDiagnostics.append(mSessionDir, "Could not read the game's mkxp.json", error);
            }
        }
        TynRuntimeDiagnostics.append(mSessionDir, "No game-folder mkxp.json found; using TYN runtime settings");
        return new JSONObject();
    }

    private static String stripJsonTrailingCommas(String json)
    {
        StringBuilder normalized = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (inString) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                normalized.append(current);
                continue;
            }
            if (current == ',') {
                int next = i + 1;
                while (next < json.length() && Character.isWhitespace(json.charAt(next))) next++;
                if (next < json.length() && (json.charAt(next) == '}' || json.charAt(next) == ']')) continue;
            }
            normalized.append(current);
        }
        return normalized.toString();
    }

    private JSONArray stringArray(Object value)
    {
        JSONArray result = new JSONArray();
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            for (int i = 0; i < source.length(); i++) {
                String item = source.optString(i, "");
                if (!item.isEmpty()) result.put(item);
            }
        } else if (value instanceof String && !((String) value).isEmpty()) {
            result.put(value);
        }
        return result;
    }

    private static int detectRgssVersion(String folder)
    {
        File data = new File(folder, "Data");
        if (new File(data, "Actors.rxdata").isFile() || new File(folder, "Game.rgssad").isFile()) return 1;
        if (new File(data, "Actors.rvdata").isFile() || new File(folder, "Game.rgss2a").isFile()) return 2;
        return 3;
    }

    private static int resolveRubyRuntime(String requested, String gameFolder)
    {
        if ("187".equals(requested)) {
            Log.i(TAG, "Ruby runtime selection: explicit Ruby 1.8.7 override");
            return 187;
        }
        if ("193".equals(requested)) {
            Log.i(TAG, "Ruby runtime selection: explicit Ruby 1.9.3 override");
            return 193;
        }
        if ("313".equals(requested)) {
            Log.i(TAG, "Ruby runtime selection: explicit Ruby 3.1.3 override");
            return 313;
        }

        EssentialsRuntimeProbe.Result essentials = EssentialsRuntimeProbe.detect(new File(gameFolder));
        if (essentials.rubyRuntime != EssentialsRuntimeProbe.UNKNOWN) {
            Log.i(TAG, "Ruby runtime auto selection: " + essentials.reason);
            return essentials.rubyRuntime;
        }

        int rgssVersion = detectRgssVersion(gameFolder);
        int runtime = rgssVersion == 1 || rgssVersion == 2 ? 187
            : rgssVersion == 3 ? 193 : 313;
        Log.i(TAG, "Ruby runtime auto selection: " + essentials.reason
            + "; falling back to RGSS generation " + rgssVersion + " -> Ruby "
            + (runtime == 187 ? "1.8.7" : runtime == 193 ? "1.9.3" : "3.1.3"));
        return runtime;
    }

    private static String shortHash(String value)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) out.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (Exception error) {
            return Long.toHexString(System.nanoTime());
        }
    }

    private boolean copyUriToFile(String uriText, File destination, int maxBytes)
    {
        try (InputStream input = getContentResolver().openInputStream(Uri.parse(uriText)); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) return false;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (bytes.size() + count > maxBytes) return false;
                bytes.write(buffer, 0, count);
            }
            writeAtomically(destination, bytes.toString("UTF-8"));
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Could not read granted content URI", error);
            TynRuntimeDiagnostics.append(mSessionDir, "Could not read a granted runtime content URI", error);
            return false;
        }
    }

    private boolean syncTranslationTable(String uriText, File destination)
    {
        try (InputStream input = getContentResolver().openInputStream(Uri.parse(uriText)); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) return false;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (bytes.size() + count > 16 * 1024 * 1024) return false;
                bytes.write(buffer, 0, count);
            }
            JSONObject map = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8).replace("\uFEFF", ""));
            String encoded = encodeTranslationMap(map);
            String current = destination.isFile() ? new String(readBytes(destination, 16 * 1024 * 1024), StandardCharsets.US_ASCII) : null;
            if (!encoded.equals(current)) {
                writeAtomically(destination, encoded);
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Translation table loaded or refreshed; entries=" + map.length());
            }
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Translation table is unavailable or invalid", error);
            long now = android.os.SystemClock.elapsedRealtime();
            if (mSessionDir != null && now - mLastTranslationFailureLogAt >= TimeUnit.SECONDS.toMillis(30)) {
                mLastTranslationFailureLogAt = now;
                TynRuntimeDiagnostics.append(mSessionDir, "Translation table read or validation failed; polling will continue", error);
            }
            return false;
        }
    }

    private static String encodeTranslationMap(JSONObject map)
    {
        StringBuilder output = new StringBuilder();
        Iterator<String> keys = map.keys();
        ArrayList<Charset> charsets = new ArrayList<>(Arrays.asList(
            StandardCharsets.UTF_8,
            Charset.forName("Shift_JIS"),
            Charset.forName("windows-1252"),
            Charset.forName("windows-1250"),
            Charset.forName("windows-1251"),
            Charset.forName("GB18030")
        ));
        while (keys.hasNext()) {
            String source = keys.next();
            Object rawTarget = map.opt(source);
            if (!(rawTarget instanceof String)) continue;
            String target = (String) rawTarget;
            if (source.isEmpty() || target.isEmpty()) continue;
            for (Charset charset : charsets) {
                byte[] sourceBytes = encodeStrict(source, charset);
                if (sourceBytes == null || sourceBytes.length == 0) continue;
                byte[] targetBytes = encodeStrict(target, charset);
                Charset targetCharset = charset;
                if (targetBytes == null) {
                    targetCharset = StandardCharsets.UTF_8;
                    targetBytes = target.getBytes(StandardCharsets.UTF_8);
                }
                output.append(hex(sourceBytes)).append('\t').append(hex(targetBytes)).append('\t')
                    .append(targetCharset.name()).append('\n');
            }
        }
        return output.toString();
    }

    private static byte[] encodeStrict(String value, Charset charset)
    {
        try {
            CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer bytes = encoder.encode(CharBuffer.wrap(value));
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static void writeAtomically(File destination, String text) throws java.io.IOException
    {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new java.io.IOException("Unable to create " + parent);
        File temporary = new File(destination.getAbsolutePath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
        } catch (Exception error) {
            throw new java.io.IOException("Unable to atomically publish " + destination, error);
        }
    }

    private static byte[] readBytes(File file, int limit) throws java.io.IOException
    {
        try (InputStream input = new java.io.FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (output.size() + count > limit) throw new java.io.IOException("File exceeds limit: " + file);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void startTranslationSync(final File table)
    {
        if (mTranslationMapUri == null) return;
        mTranslationSync = Executors.newSingleThreadScheduledExecutor();
        final String uri = mTranslationMapUri;
        mTranslationSync.scheduleWithFixedDelay(() -> syncTranslationTable(uri, table), 2, 2, TimeUnit.SECONDS);
    }

    /**
     * The activity is singleTask. If it is still alive (user pressed Home) and
     * A launcher asks for a DIFFERENT game, Ruby cannot be re-initialised in the same
     * process, so we close and the next launch starts a fresh process.
     */
    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        if (!TYN_ACTION_LAUNCH.equals(intent.getAction()) ||
            intent.getIntExtra(TYN_EXTRA_PROTOCOL_VERSION, 1) != 1) {
            Log.e(TAG, "Rejected new activity intent outside the TYN runtime-module v1 contract");
            finish();
            return;
        }
        String dir = intent.getStringExtra(TYN_EXTRA_FOLDER);
        if (!isAbsoluteDirectory(dir)) {
            Log.e(TAG, "Rejected new runtime-module intent with invalid game folder");
            finish();
            return;
        }
        if (!new File(dir).getAbsolutePath().equals(GAME_FOLDER)) {
            Toast.makeText(this, "Closing the running game. Open the new game again.", Toast.LENGTH_LONG).show();
            finish(); // onDestroy() exits the process, so the next launch is clean
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Must be before super.onCreate(): picks the game folder before SDL starts.
        applyGameDirFromIntent(getIntent());

        super.onCreate(savedInstanceState);
        if (mInvalidLaunchRequest) {
            finish();
            return;
        }
        TynRuntimeDiagnostics.append(mSessionDir, "Android SDL activity onCreate completed");

        mStorageManager = (StorageManager) getSystemService(STORAGE_SERVICE);

        // Get main OBB filepath
        final String obbPrefix = "main"; // "main", "patch"
        final int obbVersion = 1;
        OBB_MAIN_FILENAME = getObbDir() + "/" + obbPrefix + "." + obbVersion + "." + getPackageName() + ".obb";

        // Get Debug flag
        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            DEBUG = actInfo.metaData.getBoolean("mkxp_debug") || mDebugEnabled;
            TynRuntimeDiagnostics.append(mSessionDir, "Game debug/test mode configured; enabled=" + DEBUG);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to set debug flag: " + e);
            e.printStackTrace();
        }

        // Check for all files access permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                mWaitingForStoragePermission = true;
                TynRuntimeDiagnostics.append(mSessionDir,
                    "Waiting for this runtime module's all-files permission before starting the game");
                // Request all files access permission
                // TODO: AlertDialog: polite notice that mkxp-z requires All Files Access permission.
                Uri uri = Uri.parse("package:" + getPackageName());
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                startActivityForResult(intent, 110);
            }
        }

        hideSystemUi();

        // TYN controls on top of the game surface
        sInstance = this;
        if (mTynLaunch && mTranslationMapUri != null && mSessionDir != null) {
            startTranslationSync(new File(mSessionDir, "runtime_translations.hex"));
        }
        if (mLayout != null) {
            mUi = new TynRuntimeUi(
                this,
                mLayout,
                mGameId == null ? GAME_FOLDER : mGameId,
                PAD_PROFILE_PATH,
                mPadProfileUri == null ? null : Uri.parse(mPadProfileUri),
                mPadEnabled,
                mThemePrimaryColor,
                mThemeOnPrimaryColor,
                mCheatsEnabled,
                mCopyTextEnabled,
                mSpeedUp,
                mSessionDir,
                this::restoreGameSurfaceFocus,
                this::exportDiagnostics
            );
        }
    }

    private void exportDiagnostics()
    {
        if (mDiagnosticsExporting) return;
        mDiagnosticsExporting = true;
        Toast.makeText(this, R.string.runtime_info_exporting_diagnostics, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File archive = null;
            Exception failure = null;
            try {
                archive = TynRuntimeDiagnostics.createArchive(this);
            } catch (Exception error) {
                failure = error;
            }
            File result = archive;
            Exception exportFailure = failure;
            runOnUiThread(() -> {
                mDiagnosticsExporting = false;
                if (isFinishing() || isDestroyed()) return;
                if (exportFailure != null) {
                    Log.e(TAG, "Could not export runtime diagnostics", exportFailure);
                    Toast.makeText(this, R.string.runtime_diagnostics_export_failed, Toast.LENGTH_SHORT).show();
                } else if (result == null) {
                    Toast.makeText(this, R.string.runtime_diagnostics_no_logs, Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        TynRuntimeDiagnostics.share(this, result, getString(R.string.runtime_diagnostics_share_title));
                    } catch (Exception error) {
                        Log.e(TAG, "Could not share runtime diagnostics", error);
                        Toast.makeText(this, R.string.runtime_diagnostics_export_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }, "tyn-rgss-diagnostics-export").start();
    }

    /** Return keyboard/game input to SDL after the RGSS cheat WebView closes. */
    public void restoreGameSurfaceFocus()
    {
        runOnUiThread(() -> {
            if (mSurface == null || isFinishing() || isDestroyed()) return;
            View surface = mSurface;
            surface.post(() -> {
                if (mSurface == surface && !isFinishing() && !isDestroyed()) {
                    surface.requestFocus();
                    TynRuntimeDiagnostics.append(mSessionDir,
                        "Restored SDL surface input focus after closing RGSS cheat panel");
                }
            });
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        TynRuntimeDiagnostics.append(mSessionDir, "Runtime activity resumed");
    }

    /**
     * Called from native code (graphics.cpp) when the game draws its first frame.
     * Removes the loading spinner.
     */
    @SuppressWarnings("unused")
    public static void onGameFirstFrame()
    {
        MainActivity a = sInstance;
        if (a != null) {
            TynRuntimeDiagnostics.append(a.mSessionDir, "First game frame rendered; runtime reached the game screen");
            if (a.mUi != null) a.mUi.onFirstFrame();
        }
    }

    /**
     * Real full screen: hides the status bar and the gesture/navigation bar
     * (they reappear for a moment when the user swipes from the edge) and draws
     * under the camera notch. Without this the game area was cut at the top/bottom.
     */
    private void hideSystemUi()
    {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause()
    {
        if (mUi != null) mUi.onPause();
        TynRuntimeDiagnostics.append(mSessionDir, "Runtime activity paused");
        super.onPause();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        startRuntimeWhenReady();
    }

    private void startRuntimeWhenReady()
    {
        if (mInvalidLaunchRequest || isFinishing()) return;
        if (mWaitingForStoragePermission) {
            TynRuntimeDiagnostics.append(mSessionDir,
                "Native startup held until the all-files permission result is handled");
            return;
        }
        TynRuntimeDiagnostics.append(mSessionDir, mStarted
            ? "Runtime activity entered onStart after a previous start"
            : "Runtime activity entered onStart; native runtime startup is next");

        if (!mStarted) {
            // Check for main OBB file
            if (!mCustomGameDir && new File(OBB_MAIN_FILENAME).exists()) {
                Log.v(TAG, "Main OBB file found, starting with main OBB mount");

                // Try to mount main OBB file
                mStorageManager.mountObb(OBB_MAIN_FILENAME, null, obbListener);
            } else {
                Log.v(TAG, "Main OBB file not found, starting without main OBB mount");

                // Run from default game directory
                runSDLThread();
            }
        } else {
            // onStart: Resume SDL thread
            runSDLThread();
        }
    }

    @Override
    protected void onDestroy()
    {
        TynRuntimeDiagnostics.append(mSessionDir, "Runtime activity destroyed; clean process exit requested");
        if (mTranslationSync != null) {
            mTranslationSync.shutdownNow();
            mTranslationSync = null;
        }
        if (mUi != null) mUi.onDestroy();
        sInstance = null;
        super.onDestroy();

        // Notify TYN only after SDL teardown. If Android kills the process without this callback,
        // the App's durable launch marker intentionally remains and can be cleared by confirmation.
        if (mTynLaunch && isFinishing() && !isChangingConfigurations()) {
            sendTynSaveSessionEndedCallback();
        }

        // HACK: Exiting the JVM (process) since Ruby does not likes when we
        // trying to re-initialize Ruby VM in mkxp-z (JNI native library)
        // that leads to segmentation fault, even we have cleanup the Ruby VM.
        System.exit(0);
    }

    private void sendTynSaveSessionEndedCallback()
    {
        if (mTynSaveSessionEndedCallback == null || mTynSaveSessionEndedCallbackSent) return;
        mTynSaveSessionEndedCallbackSent = true;
        try {
            mTynSaveSessionEndedCallback.send();
        } catch (PendingIntent.CanceledException error) {
            Log.w(TAG, "TYN save-session completion callback was canceled", error);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        // Back button: show the module's close-game dialog.
        if (evt.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (evt.getAction() == KeyEvent.ACTION_UP && mUi != null) {
                mUi.onBack();
            }
            return true;
        }
        return super.dispatchKeyEvent(evt);
    }

    /**
     * This method is for arguments for launching native mkxp-z.
     * 
     * @return arguments for the mkxp-z
     */
    @Override
    protected String[] getArguments()
    {
        String[] args;

        if (DEBUG) {
            // Arguments in Debug mode
            args = new String[] { "debug" };
        } else {
            // Arguments in normal mode
            args = new String[] {};
        }

        return args;
    }

    /**
     * This static method is used in native mkxp-z. (see systemImpl.cpp)
     * This method returns a string of current device locale tag. (e.g. "en_US")
     * 
     * @return string of locale tag
     */
    @SuppressWarnings("unused")
    private static String getSystemLanguage()
    {
        return Locale.getDefault().toString();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method returns a boolean indicating that the device has a vibrator or not.
     * 
     * @return boolean
     */
    @SuppressWarnings("unused")
    private static boolean hasVibrator()
    {
        Vibrator vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        return vib.hasVibrator();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method makes device vibrating with given milliseconds duration.
     * 
     * @param duration milliseconds duration of vibration
     */
    @SuppressWarnings("unused")
    private static void vibrate(int duration)
    {
        Vibrator vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            vib.vibrate(duration);
        }
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method turns off the current device vibration.
     */
    @SuppressWarnings("unused")
    private static void vibrateStop()
    {
        Vibrator vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        vib.cancel();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method returns a boolean indicating the app is in multi window mode or not.
     * (Multi-window mode supports from Android 7.0 Nougat (API 24) and higher.)
     * 
     * @param activity current MainActivity instance
     * @return boolean
     */
    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}
