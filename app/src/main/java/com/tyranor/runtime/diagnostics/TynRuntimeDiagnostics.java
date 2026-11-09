package com.tyranor.runtime.diagnostics;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.system.Os;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.hatkid.mkxpz.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a per-session runtime timeline and exports all saved sessions with Ruby error logs. */
public final class TynRuntimeDiagnostics {
    private static final String TAG = "TYN-RGSS-Diagnostics";
    private static final String SESSION_LOG_NAME = "TynRuntimeDebug.txt";
    private static final String NATIVE_LOG_NAME = "NativeRuntime.txt";
    private static final String RUBY_LOG_NAME = "AnyPlayLog.txt";
    private static final String SESSION_ENV = "TYN_RGSS_DIAGNOSTICS_DIR";
    private static final Object APPEND_LOCK = new Object();
    private static volatile long sessionStartedElapsedRealtime;
    private static volatile File uncaughtExceptionSessionDir;
    private static volatile boolean verboseInputDiagnostics;
    private static boolean exceptionHandlerInstalled;

    private TynRuntimeDiagnostics() {}

    public static void beginSession(
        Context context,
        File sessionDir,
        String gameTitle,
        File gameFolder,
        int rubyRuntime,
        int rgssVersion,
        boolean translationSelected,
        boolean padEnabled,
        String options,
        long launchRequestedAtMillis,
        long launchRequestedAtElapsedRealtime
    ) {
        if (sessionDir == null) return;
        File log = new File(sessionDir, SESSION_LOG_NAME);
        try {
            if (!sessionDir.isDirectory() && !sessionDir.mkdirs()) {
                throw new IOException("Could not create private session directory");
            }
            sessionStartedElapsedRealtime = launchRequestedAtElapsedRealtime > 0
                ? launchRequestedAtElapsedRealtime
                : SystemClock.elapsedRealtime();
            uncaughtExceptionSessionDir = sessionDir;
            verboseInputDiagnostics = BuildConfig.TYN_VERBOSE_DIAGNOSTICS;
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(log, false), StandardCharsets.UTF_8))) {
                Date now = new Date(launchRequestedAtMillis > 0 ? launchRequestedAtMillis : System.currentTimeMillis());
                writer.write("TYN RGSS runtime diagnostics\n");
                writer.write("Started: " + formatTimestamp(now) + "\n");
                writer.write("Started UTC: " + formatUtcTimestamp(now) + "\n");
                writer.write("Process ID: " + android.os.Process.myPid() + "\n");
                writer.write("Diagnostics initialized: " + formatTimestamp(new Date()) + "\n");
                writer.write("Module version: " + BuildConfig.VERSION_NAME + "\n");
                writer.write("Module package: " + context.getPackageName() + "\n");
                writer.write("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
                writer.write("Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL) + "\n");
                writer.write("ABIs: " + join(Build.SUPPORTED_ABIS) + "\n");
                String displayGameTitle = gameTitle == null || gameTitle.trim().isEmpty()
                    ? (gameFolder == null ? "Unknown game" : gameFolder.getName())
                    : gameTitle;
                writer.write("Game: " + safe(displayGameTitle) + "\n");
                writer.write("Game folder name: " + (gameFolder == null ? "?" : safe(gameFolder.getName())) + "\n");
                writer.write("Selected Ruby: " + rubyName(rubyRuntime) + "\n");
                writer.write("Detected RGSS generation: " + rgssVersion + "\n");
                writer.write("Standard Ruby baseline for this generation: " + (rgssVersion >= 3 ? "1.9.3" : "1.8.7") + "\n");
                writer.write("Native libraries: " + librarySet(rubyRuntime) + "\n");
                writer.write("Translation selected: " + translationSelected + "\n");
                writer.write("Virtual pad enabled: " + padEnabled + "\n");
                writer.write("mkxp options: " + safe(options) + "\n");
                writer.write("Game markers:\n");
                for (String marker : new String[] {
                    "Game.ini", "Game.rgssad", "Game.rgss2a", "Game.rgss3a",
                    "Data/Actors.rxdata", "Data/Actors.rvdata", "Data/Actors.rvdata2",
                    "Graphics/System/Window.png"
                }) {
                    writer.write("  " + marker + "=" + (gameFolder != null && new File(gameFolder, marker).isFile()) + "\n");
                }
            }
            installUncaughtExceptionLogger(sessionDir);
            Os.setenv(SESSION_ENV, sessionDir.getAbsolutePath(), true);
            if (gameFolder != null) {
                Os.setenv("TYN_RGSS_GAME_FOLDER_FOR_LOG", gameFolder.getAbsolutePath(), true);
            }
            append(sessionDir, "Session created; preparing private mkxp configuration");
            Log.i(TAG, "Started diagnostics for " + safe(gameTitle) + ", Ruby " + rubyName(rubyRuntime) + ", RGSS " + rgssVersion);
        } catch (Exception error) {
            Log.e(TAG, "Could not initialize private runtime diagnostics", error);
            append(sessionDir, "Diagnostics initialization failed", error);
        }
    }

    /** Adds a timestamped event to this game's private session timeline. */
    public static void append(File sessionDir, String message) {
        append(sessionDir, message, null);
    }

    public static void append(File sessionDir, String message, Throwable error) {
        String line = "[" + formatTimestamp(new Date()) + "][+" + elapsedSessionMillis() + " ms] "
            + safe(message) + "\n";
        if (error != null) {
            String stack = Log.getStackTraceString(error);
            if (stack.length() > 64_000) stack = stack.substring(0, 64_000) + "\n[stack trace truncated]\n";
            line += stack + "\n";
        }
        Log.i(TAG, line.trim());
        if (sessionDir == null) return;
        synchronized (APPEND_LOCK) {
            try (FileOutputStream output = new FileOutputStream(new File(sessionDir, SESSION_LOG_NAME), true)) {
                output.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (IOException writeError) {
                Log.e(TAG, "Could not append to private runtime diagnostics", writeError);
            }
        }
    }

    /** Enables input-event tracing for diagnostic builds or an explicitly enabled debug session. */
    public static void setVerboseInputDiagnostics(boolean enabled) {
        verboseInputDiagnostics = enabled;
        append(uncaughtExceptionSessionDir, "Input event tracing enabled=" + enabled);
    }

    /** Records Enter routing without ever recording committed or typed text. */
    public static void appendInputEvent(String message) {
        if (!verboseInputDiagnostics) return;
        File sessionDir = uncaughtExceptionSessionDir;
        if (sessionDir != null) append(sessionDir, "Input: " + message);
    }

    /** Builds one ZIP containing the complete diagnostic logs from every saved game session. */
    public static File createArchive(Context context) throws IOException {
        File sessionsRoot = new File(context.getFilesDir(), "tyranor/sessions");
        File[] sessionDirs = sessionsRoot.listFiles(File::isDirectory);
        if (sessionDirs == null || sessionDirs.length == 0) return null;

        List<SessionLogs> sessions = new ArrayList<>();
        for (File sessionDir : sessionDirs) {
            SessionLogs session = describeSession(sessionDir);
            if (session.hasLogs()) sessions.add(session);
        }
        if (sessions.isEmpty()) return null;
        sessions.sort(Comparator.comparingLong(session -> session.startedAtMillis));

        // The game's ScriptLoader writes the original exception details to the module's
        // files directory, outside the per-session folder. Include it because the on-screen
        // error dialog only reports this path and otherwise the root cause is omitted.
        File gameErrorLog = new File(context.getFilesDir(), "errorlog.txt");

        File exportDir = new File(context.getCacheDir(), "tyranor-diagnostics");
        if (!exportDir.isDirectory() && !exportDir.mkdirs()) {
            throw new IOException("Could not create diagnostics export directory");
        }
        String archiveName = "TYN-RGSS-diagnostics-"
            + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date())
            + "-" + Long.toHexString(System.nanoTime()) + ".zip";
        File archive = new File(exportDir, archiveName);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive)))) {
            zip.setLevel(6);
            putTextEntry(zip, "index.txt", buildIndex(sessions, gameErrorLog.isFile()));
            copyLog(zip, gameErrorLog, "module/errorlog.txt");
            int index = 0;
            for (SessionLogs session : sessions) {
                index++;
                String sessionPath = String.format(Locale.ROOT, "sessions/%03d_%s_%s/", index,
                    new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(session.startedAtMillis)),
                    safeFileComponent(session.gameTitle) + "_" + safeFileComponent(session.directory.getName()));
                copyLog(zip, session.diagnosticsLog, sessionPath + SESSION_LOG_NAME);
                copyLog(zip, session.nativeLog, sessionPath + NATIVE_LOG_NAME);
                copyLog(zip, session.rubyLog, sessionPath + RUBY_LOG_NAME);
            }
        }
        return archive;
    }

    public static void share(Activity activity, File archive, String chooserTitle) throws IOException {
        Uri uri = FileProvider.getUriForFile(
            activity,
            activity.getPackageName() + ".diagnostics",
            archive
        );
        Intent send = new Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_SUBJECT, "TYN RGSS complete diagnostics")
            .putExtra(Intent.EXTRA_TEXT, "Complete TYN RGSS diagnostics archive containing all saved game sessions.")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(activity.getContentResolver(), "TYN RGSS diagnostics", uri));
        activity.startActivity(Intent.createChooser(send, chooserTitle));
    }

    private static void installUncaughtExceptionLogger(File sessionDir) {
        synchronized (TynRuntimeDiagnostics.class) {
            uncaughtExceptionSessionDir = sessionDir;
            if (exceptionHandlerInstalled) return;
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                append(uncaughtExceptionSessionDir,
                    "Uncaught Java exception on thread " + safe(thread == null ? null : thread.getName()), error);
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            });
            exceptionHandlerInstalled = true;
        }
    }

    private static SessionLogs describeSession(File directory) {
        File diagnosticsLog = new File(directory, SESSION_LOG_NAME);
        File nativeLog = new File(directory, NATIVE_LOG_NAME);
        File rubyLog = new File(directory, RUBY_LOG_NAME);
        Map<String, String> fields = new LinkedHashMap<>();
        if (diagnosticsLog.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(diagnosticsLog), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int separator = line.indexOf(':');
                    if (separator > 0) {
                        String key = line.substring(0, separator).trim();
                        if ("Started".equals(key) || "Game".equals(key) || "Selected Ruby".equals(key)
                            || "Detected RGSS generation".equals(key)) {
                            fields.put(key, line.substring(separator + 1).trim());
                        }
                    }
                    if (fields.size() >= 4) break;
                }
            } catch (IOException error) {
                Log.w(TAG, "Could not read session metadata from " + directory.getName(), error);
            }
        }
        String started = fields.get("Started");
        return new SessionLogs(
            directory,
            diagnosticsLog.isFile() ? diagnosticsLog : null,
            nativeLog.isFile() ? nativeLog : null,
            rubyLog.isFile() ? rubyLog : null,
            parseTimestamp(started, directory.lastModified()),
            started == null ? "Unknown start time" : started,
            fields.getOrDefault("Game", "Unknown game"),
            fields.getOrDefault("Selected Ruby", "Unknown Ruby"),
            fields.getOrDefault("Detected RGSS generation", "?"),
            containsText(diagnosticsLog, "First game frame rendered"),
            containsText(diagnosticsLog, "clean process exit requested")
        );
    }

    private static String buildIndex(List<SessionLogs> sessions, boolean gameErrorLogIncluded) {
        StringBuilder index = new StringBuilder();
        index.append("TYN RGSS complete diagnostics archive\n");
        index.append("Exported: ").append(formatTimestamp(new Date())).append("\n");
        index.append("Sessions included: ").append(sessions.size()).append("\n");
        index.append("Each session folder contains every available TYN timeline, native runtime log, and Ruby error log.\n");
        index.append("Module-level game script error log included: ").append(gameErrorLogIncluded).append("\n");
        if (gameErrorLogIncluded) index.append("See module/errorlog.txt for original game script load exceptions.\n");
        index.append("Game folder paths and game assets are not included.\n\n");
        for (int i = 0; i < sessions.size(); i++) {
            SessionLogs session = sessions.get(i);
            index.append(String.format(Locale.ROOT, "%03d. ", i + 1))
                .append(session.startedAtText)
                .append(" | game=").append(safe(session.gameTitle))
                .append(" | Ruby=").append(safe(session.rubyRuntime))
                .append(" | RGSS=").append(safe(session.rgssGeneration))
                .append(" | diagnostics=").append(session.diagnosticsLog != null)
                .append(" | native log=").append(session.nativeLog != null)
                .append(" | Ruby log=").append(session.rubyLog != null)
                .append(" | first frame=").append(session.firstFrameRendered)
                .append(" | clean exit=").append(session.cleanExitRecorded)
                .append(" | session id=").append(safe(session.directory.getName()))
                .append('\n');
        }
        return index.toString();
    }

    private static void copyLog(ZipOutputStream zip, File source, String entryName) throws IOException {
        if (source == null || !source.isFile()) return;
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }

    private static void putTextEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static long parseTimestamp(String value, long fallback) {
        if (value == null) return fallback;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).parse(value).getTime();
        } catch (ParseException error) {
            return fallback;
        }
    }

    private static boolean containsText(File source, String marker) {
        if (source == null || !source.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(marker)) return true;
            }
        } catch (IOException error) {
            Log.w(TAG, "Could not inspect session marker in " + source.getName(), error);
        }
        return false;
    }

    private static String safeFileComponent(String value) {
        String component = safe(value).replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]+", "_").trim();
        if (component.isEmpty()) component = "unknown";
        return component.length() > 48 ? component.substring(0, 48) : component;
    }

    private static long elapsedSessionMillis() {
        long startedAt = sessionStartedElapsedRealtime;
        return startedAt == 0 ? 0 : Math.max(0, SystemClock.elapsedRealtime() - startedAt);
    }

    private static String formatTimestamp(Date value) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(value);
    }

    private static String formatUtcTimestamp(Date value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(value);
    }

    private static String rubyName(int version) {
        if (version == 187) return "1.8.7";
        if (version == 193) return "1.9.3";
        return "3.1.3 (" + version + ")";
    }

    private static String librarySet(int version) {
        if (version == 187) return "ruby187 + mkxp-z-187";
        if (version == 193) return "ruby193 + mkxp-z-193";
        return "ruby + mkxp-z";
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "?";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(", ");
            out.append(safe(value));
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "?" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final class SessionLogs {
        final File directory;
        final File diagnosticsLog;
        final File nativeLog;
        final File rubyLog;
        final long startedAtMillis;
        final String startedAtText;
        final String gameTitle;
        final String rubyRuntime;
        final String rgssGeneration;
        final boolean firstFrameRendered;
        final boolean cleanExitRecorded;

        SessionLogs(File directory, File diagnosticsLog, File nativeLog, File rubyLog, long startedAtMillis,
                    String startedAtText, String gameTitle, String rubyRuntime, String rgssGeneration,
                    boolean firstFrameRendered, boolean cleanExitRecorded) {
            this.directory = directory;
            this.diagnosticsLog = diagnosticsLog;
            this.nativeLog = nativeLog;
            this.rubyLog = rubyLog;
            this.startedAtMillis = startedAtMillis;
            this.startedAtText = startedAtText;
            this.gameTitle = gameTitle;
            this.rubyRuntime = rubyRuntime;
            this.rgssGeneration = rgssGeneration;
            this.firstFrameRendered = firstFrameRendered;
            this.cleanExitRecorded = cleanExitRecorded;
        }

        boolean hasLogs() {
            return diagnosticsLog != null || nativeLog != null || rubyLog != null;
        }
    }
}
