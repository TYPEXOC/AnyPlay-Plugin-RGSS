package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.storage.StorageManager;
import android.os.storage.OnObbStateChangeListener;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;
import java.io.File;
import java.io.RandomAccessFile;

import org.libsdl.app.SDL;
import org.libsdl.app.SDLActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import com.anyplay.overlay.AnyPlayUi;

public class MainActivity extends SDLActivity
{
    // This activity inherits from SDLActivity activity.
    // Put your Java-side stuff here.

    private static final String TAG = "mkxp-z[Activity]";
    private static final String GAME_PATH_DEFAULT = Environment.getExternalStorageDirectory() + "/mkxp-z";
    private static String GAME_PATH = GAME_PATH_DEFAULT;
    private static String OBB_MAIN_FILENAME;
    private static boolean DEBUG = false;

    // ---- AnyPlay integration -------------------------------------------
    // AnyPlay starts this activity with action "com.anyplay.rgss.LAUNCH" and the
    // full game folder in the string extra "game_dir". When it is present we use
    // that folder INSTEAD of the fixed /storage/emulated/0/mkxp-z one. Opening
    // the app normally (launcher icon) still uses the default folder.
    private static final String EXTRA_GAME_DIR = "game_dir";
    private static final String EXTRA_RUNTIME_MODE = "runtime_mode";
    private boolean mCustomGameDir = false;

    // Runtime ids. A runtime = one libruby + one engine library, loaded together.
    // One process = one runtime: Ruby cannot be initialised twice in a process,
    // so a different runtime always needs a fresh process (see onNewIntent).
    //   r3   : Ruby 3.1, no compatibility patches      (default)
    //   r18  : Ruby 3.1 + Ruby 1.8 compatibility layer  (game code sees Ruby 1.8.7)
    //   r34  : Ruby 3.4 (+ YJIT on arm64 if the APK was built with it). Never picked
    //          automatically; only by runtime_mode / anyplay_runtime.txt, and only if
    //          the APK really contains it (otherwise r3 is used and the log says so).
    private static final String RT_R3 = "r3";
    private static final String RT_R18 = "r18";
    private static final String RT_R34 = "r34";    // optional: only in APKs built with BUILD_R34=1
    private static String sRuntimeId = RT_R3;
    private static String sNativeLibDir = null;    // set in onCreate / onNewIntent
    private static String sRuntimeReason = "";

    // Phase C: which runtime "auto" picks for an RGSS1 (RPG Maker XP, Ruby 1.8) game.
    // RGSS2/RGSS3 games and games that cannot be identified always get RT_R3.
    // If the r18 runtime ever misbehaves on a device, change this one line to RT_R3
    // and every game goes back to the clean Ruby 3.1 runtime (explicit runtime_mode and
    // anyplay_runtime.txt still work).
    private static final String RGSS1_AUTO_RUNTIME = RT_R18;

    protected boolean mStarted = false;

    private StorageManager mStorageManager;

    // AnyPlay overlay (gamepad, keyboard, loading spinner, Close-game dialog)
    private AnyPlayUi mUi;
    private static MainActivity sInstance;

    private void runSDLThread()
    {
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
        }

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
        if (requestCode == 110) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                // Close the App because the User did not allow the all files access permission to be used.
                mSingleton.finish();
            }
        }
    }

    /**
     * Reads "game_dir" from the launching intent and, if valid, makes it the
     * game folder. The native side reads the static GAME_PATH field when the SDL
     * thread starts (see main.cpp), so this must run before that (onCreate).
     */
    private void applyGameDirFromIntent(Intent intent)
    {
        if (intent == null) return;

        String dir = intent.getStringExtra(EXTRA_GAME_DIR);
        if (dir == null || dir.isEmpty()) return;

        File f = new File(dir);
        if (!f.isDirectory()) {
            Log.e(TAG, "game_dir does not exist or is not a folder: " + dir);
            return; // fall back to the default folder; native side shows an error if that is missing too
        }

        GAME_PATH = f.getAbsolutePath();
        mCustomGameDir = true;
        Log.i(TAG, "Game folder from AnyPlay: " + GAME_PATH);
    }

    /**
     * Maps the "runtime_mode" extra to a runtime id, or null when the value is
     * missing or unknown.
     *   r3,  ruby3   -> r3
     *   r18, ruby18, r187 -> r18  (Ruby 1.8 compatibility runtime; the real Ruby
     *                          1.8.7 build was removed in Phase B, so the old
     *                          names the launcher may still send map to r18)
     *   r34, ruby34, ruby3_plus, ruby3plus -> r34  (Ruby 3.4)
     */
    private static String runtimeFromMode(String mode)
    {
        if (mode == null) return null;
        switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "r3":
            case "ruby3":
                return RT_R3;
            case "r18":
            case "ruby18":
            case "r187":
                return RT_R18;
            case "r34":
            case "ruby34":
            case "ruby3_plus":
            case "ruby3plus":
                return RT_R34;
            default:
                return null;
        }
    }

    /** The runtime picked for a game and a short ASCII reason for the log. */
    private static final class RuntimeChoice
    {
        final String id;
        final String reason;

        RuntimeChoice(String id, String reason)
        {
            this.id = id;
            this.reason = reason;
        }
    }

    private static boolean isAutoMode(String mode)
    {
        if (mode == null) return true;
        String m = mode.trim().toLowerCase(Locale.ROOT);
        return m.isEmpty() || m.equals("auto");
    }

    /**
     * Decides the runtime for a game folder. Order (first match wins):
     *   1. runtime_mode extra from the launcher, when it names a runtime (not "auto")
     *   2. anyplay_runtime.txt in the game folder (r3 / r18 / auto), per-game override
     *   3. auto-detect: RGSS1 -> RGSS1_AUTO_RUNTIME, RGSS2/RGSS3 -> r3
     *   4. anything unknown -> r3 (the safe default)
     * Used both at start-up and by onNewIntent, so both always agree.
     */
    private static RuntimeChoice resolveRuntime(String requestedMode, String gameDir)
    {
        RuntimeChoice c = resolveRuntimeRaw(requestedMode, gameDir);
        if (!isRuntimeInstalled(c.id)) {
            return new RuntimeChoice(RT_R3, "wanted " + c.id + " (" + c.reason + ") but this APK does not contain it,"
                    + " using r3");
        }
        return c;
    }

    /** r3 and r18 are always in the APK. r34 only when it was built with BUILD_R34=1. */
    private static boolean isRuntimeInstalled(String id)
    {
        if (!RT_R34.equals(id)) return true;
        if (sNativeLibDir == null) return false;
        String[] libs = runtimeLibs(id);
        for (String lib : libs) {
            if (!new File(sNativeLibDir, "lib" + lib + ".so").isFile()) return false;
        }
        return true;
    }

    private static RuntimeChoice resolveRuntimeRaw(String requestedMode, String gameDir)
    {
        String warn = "";

        // 1. launcher
        String id = runtimeFromMode(requestedMode);
        if (id != null) {
            return new RuntimeChoice(id, "launcher runtime_mode=" + requestedMode.trim());
        }
        if (!isAutoMode(requestedMode)) {
            warn = " (unknown runtime_mode \"" + requestedMode + "\" ignored)";
        }

        File dir = (gameDir == null || gameDir.isEmpty()) ? null : new File(gameDir);

        // 2. per-game override file
        String override = RuntimeDetector.readOverride(dir);
        if (override != null && !override.equals("auto")) {
            String oid = runtimeFromMode(override);
            if (oid != null) {
                return new RuntimeChoice(oid, "game folder " + RuntimeDetector.OVERRIDE_FILE + "=" + override + warn);
            }
            warn += " (unknown value \"" + override + "\" in " + RuntimeDetector.OVERRIDE_FILE + " ignored)";
        }

        // 3. auto-detect
        RuntimeDetector.Detection d = RuntimeDetector.detect(dir);
        if (d.rgss == 1) {
            return new RuntimeChoice(RGSS1_AUTO_RUNTIME, "auto: " + d.reason + warn);
        }
        if (d.rgss == 2 || d.rgss == 3) {
            return new RuntimeChoice(RT_R3, "auto: " + d.reason + warn);
        }
        return new RuntimeChoice(RT_R3, "auto: unknown, safe default (" + d.reason + ")" + warn);
    }

    /**
     * Select a native VM before SDL loads any libraries (see resolveRuntime for the
     * rules). The result is also exported as the environment variable
     * ANYPLAY_RUNTIME_REASON so that AnyPlayLog can print why this runtime was chosen.
     */
    private void selectRubyRuntime(Intent intent)
    {
        String requested = intent == null ? null : intent.getStringExtra(EXTRA_RUNTIME_MODE);
        RuntimeChoice c = resolveRuntime(requested, GAME_PATH);
        sRuntimeId = c.id;
        sRuntimeReason = c.reason;
        Log.i(TAG, "Runtime " + c.id + " chosen: " + c.reason);
        try {
            android.system.Os.setenv("ANYPLAY_RUNTIME_REASON", c.reason, true);
        } catch (Exception e) {
            Log.w(TAG, "Could not export ANYPLAY_RUNTIME_REASON: " + e);
        }
    }

    /**
     * File names (without "lib" and ".so") of the Ruby library and the engine
     * library of a runtime. The Ruby library is listed BEFORE the engine on purpose:
     * the engine records "NEEDED libruby.so", and Android's linker satisfies that with
     * the library that is already loaded and has that internal SONAME. The APK has no
     * file named libruby.so, so a wrong runtime can never be picked up by accident.
     */
    private static String[] runtimeLibs(String id)
    {
        if (RT_R18.equals(id)) return new String[] { "ruby_r18", "mkxp-z-r18" };
        if (RT_R34.equals(id)) return new String[] { "ruby_r34", "mkxp-z-r34" };
        return new String[] { "ruby_r3", "mkxp-z-r3" };
    }

    @Override
    protected String[] getLibraries()
    {
        String[] rt = runtimeLibs(sRuntimeId);
        return new String[] {
            "SDL2", "SDL2_image", "SDL2_ttf", "SDL2_sound", "openal",
            rt[0], rt[1]
        };
    }

    /** The engine library of the selected runtime (the one that exports SDL_main). */
    @Override
    protected String getMainSharedObject()
    {
        String engine = runtimeLibs(sRuntimeId)[1];
        return getContext().getApplicationInfo().nativeLibraryDir + "/lib" + engine + ".so";
    }

    @Override
    public void loadLibraries()
    {
        Log.i(TAG, "Loading runtime " + sRuntimeId + ": " + Arrays.toString(getLibraries()));
        for (String lib : getLibraries()) {
            try {
                SDL.loadLibrary(lib);
            } catch (UnsatisfiedLinkError e) {
                // Typical message if the SONAME match did not work:
                //   dlopen failed: library "libruby.so" not found: needed by ...
                Log.e(TAG, "Could not load native library \"" + lib + "\" (runtime "
                        + sRuntimeId + "): " + e.getMessage());
                throw e;
            }
        }
    }

    private static boolean isRuby18ProductVersion(String version)
    {
        if (version == null) return false;
        String v = version.trim().replaceAll("\\s+", "");
        return v.equals("1") || v.startsWith("1.") || v.startsWith("1,");
    }

    private static File findGameExecutable(File gameDir)
    {
        File preferred = new File(gameDir, "Game.exe");
        if (preferred.isFile()) return preferred;
        File[] files = gameDir.listFiles();
        if (files == null) return null;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) return file;
        }
        return null;
    }

    /** Read the UTF-16LE ProductVersion string from a Windows version resource. */
    private static String readProductVersion(File exe)
    {
        final byte[] key = new byte[] {
            'P', 0, 'r', 0, 'o', 0, 'd', 0, 'u', 0, 'c', 0, 't', 0,
            'V', 0, 'e', 0, 'r', 0, 's', 0, 'i', 0, 'o', 0, 'n', 0
        };
        try (RandomAccessFile input = new RandomAccessFile(exe, "r")) {
            long length = input.length();
            if (length <= 0 || length > 64L * 1024L * 1024L) return null;
            byte[] data = new byte[(int) length];
            input.readFully(data);
            for (int i = 0; i <= data.length - key.length; i++) {
                boolean found = true;
                for (int j = 0; j < key.length; j++) {
                    if (data[i + j] != key[j]) { found = false; break; }
                }
                if (!found) continue;
                int p = i + key.length;
                while (p + 1 < data.length && data[p] == 0 && data[p + 1] == 0) p += 2;
                StringBuilder value = new StringBuilder();
                while (p + 1 < data.length && data[p + 1] == 0) {
                    int ch = data[p] & 0xff;
                    if (ch == 0) break;
                    if (ch < 0x20 || ch > 0x7e) break;
                    value.append((char) ch);
                    p += 2;
                }
                String candidate = value.toString().trim();
                if (!candidate.isEmpty()) return candidate;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read ProductVersion from " + exe.getName(), e);
        }
        return null;
    }

    /**
     * The activity is singleTask. If it is still alive (user pressed Home) and
     * AnyPlay asks for a DIFFERENT game, Ruby cannot be re-initialised in the same
     * process, so we close and the next launch starts a fresh process.
     */
    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        sNativeLibDir = getApplicationInfo().nativeLibraryDir;
        String dir = intent.getStringExtra(EXTRA_GAME_DIR);
        String mode = intent.getStringExtra(EXTRA_RUNTIME_MODE);
        boolean anyplayLaunch = (dir != null && !dir.isEmpty()) || (mode != null && !mode.isEmpty());

        boolean otherGame = dir != null && !dir.isEmpty()
                && !new File(dir).getAbsolutePath().equals(GAME_PATH);

        // Same rules as selectRubyRuntime() (resolveRuntime), applied to the game folder
        // of the NEW launch. Only checked for a real AnyPlay launch, so e.g. a USB-attach
        // intent never closes a running game.
        String wantedDir = (dir != null && !dir.isEmpty()) ? dir : GAME_PATH;
        String wanted = anyplayLaunch ? resolveRuntime(mode, wantedDir).id : sRuntimeId;
        boolean otherRuntime = anyplayLaunch && !wanted.equals(sRuntimeId);

        if (otherGame || otherRuntime) {
            Log.i(TAG, "New launch needs a fresh process (otherGame=" + otherGame
                    + ", otherRuntime=" + otherRuntime + ", running=" + sRuntimeId + ", wanted=" + wanted + ")");
            Toast.makeText(this, "Closing the running game. Open the new game again.", Toast.LENGTH_LONG).show();
            finish(); // onDestroy() exits the process, so the next launch is clean
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Must be before super.onCreate(): picks the game folder before SDL starts.
        applyGameDirFromIntent(getIntent());
        sNativeLibDir = getApplicationInfo().nativeLibraryDir;
        selectRubyRuntime(getIntent());

        super.onCreate(savedInstanceState);

        mStorageManager = (StorageManager) getSystemService(STORAGE_SERVICE);

        // Get main OBB filepath
        final String obbPrefix = "main"; // "main", "patch"
        final int obbVersion = 1;
        OBB_MAIN_FILENAME = getObbDir() + "/" + obbPrefix + "." + obbVersion + "." + getPackageName() + ".obb";

        // Get Debug flag
        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            DEBUG = actInfo.metaData.getBoolean("mkxp_debug");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to set debug flag: " + e);
            e.printStackTrace();
        }

        // Check for all files access permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Request all files access permission
                // TODO: AlertDialog: polite notice that mkxp-z requires All Files Access permission.
                Uri uri = Uri.parse("package:" + getPackageName());
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                startActivityForResult(intent, 110);
            }
        }

        hideSystemUi();

        // AnyPlay overlay on top of the game surface
        sInstance = this;
        if (mLayout != null) {
            mUi = new AnyPlayUi(this, mLayout, GAME_PATH);
        }
    }

    /**
     * Called from native code (graphics.cpp) when the game draws its first frame.
     * Removes the loading spinner.
     */
    @SuppressWarnings("unused")
    public static void onGameFirstFrame()
    {
        MainActivity a = sInstance;
        if (a != null && a.mUi != null) a.mUi.onFirstFrame();
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
        super.onPause();
    }

    @Override
    protected void onStart()
    {
        super.onStart();

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
        if (mUi != null) mUi.onDestroy();
        sInstance = null;
        super.onDestroy();

        // HACK: Exiting the JVM (process) since Ruby does not likes when we
        // trying to re-initialize Ruby VM in mkxp-z (JNI native library)
        // that leads to segmentation fault, even we have cleanup the Ruby VM.
        System.exit(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        // Back button: show the "Close game?" dialog (same as the AnyPlay app)
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
