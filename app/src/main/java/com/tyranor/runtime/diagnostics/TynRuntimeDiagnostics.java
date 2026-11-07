package com.tyranor.runtime.diagnostics;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.system.Os;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.hatkid.mkxpz.BuildConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Captures a small private startup report and shares it together with mkxp-z's Ruby error log. */
public final class TynRuntimeDiagnostics {
    private static final String TAG = "TYN-RGSS-Diagnostics";
    private static final String SESSION_LOG_NAME = "TynRuntimeDebug.txt";
    private static final String RUBY_LOG_NAME = "AnyPlayLog.txt";
    private static final String SESSION_ENV = "TYN_RGSS_DIAGNOSTICS_DIR";
    private static final int MAX_REPORT_BYTES = 750_000;

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
        String options
    ) {
        if (sessionDir == null) return;
        File log = new File(sessionDir, SESSION_LOG_NAME);
        try {
            if (!sessionDir.isDirectory() && !sessionDir.mkdirs()) {
                throw new IOException("Could not create private session directory");
            }
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(log, false), StandardCharsets.UTF_8))) {
                writer.write("TYN RGSS runtime diagnostics\n");
                writer.write("Started: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.ROOT).format(new Date()) + "\n");
                writer.write("Module version: " + BuildConfig.VERSION_NAME + "\n");
                writer.write("Module package: " + context.getPackageName() + "\n");
                writer.write("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
                writer.write("Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL) + "\n");
                writer.write("ABIs: " + join(Build.SUPPORTED_ABIS) + "\n");
                writer.write("Game: " + safe(gameTitle) + "\n");
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
            Os.setenv(SESSION_ENV, sessionDir.getAbsolutePath(), true);
            append(sessionDir, "Ruby error log target: module-private session");
            Log.i(TAG, "Started diagnostics for " + safe(gameTitle) + ", Ruby " + rubyName(rubyRuntime) + ", RGSS " + rgssVersion);
        } catch (Exception error) {
            Log.e(TAG, "Could not initialize private runtime diagnostics", error);
            append(sessionDir, "diagnostics initialization failed", error);
        }
    }

    public static void append(File sessionDir, String message) {
        append(sessionDir, message, null);
    }

    public static void append(File sessionDir, String message, Throwable error) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date())
            + " " + safe(message) + "\n";
        if (error != null) line += Log.getStackTraceString(error) + "\n";
        Log.i(TAG, line.trim());
        if (sessionDir == null) return;
        try (FileOutputStream output = new FileOutputStream(new File(sessionDir, SESSION_LOG_NAME), true)) {
            output.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException writeError) {
            Log.e(TAG, "Could not append to private runtime diagnostics", writeError);
        }
    }

    /** Returns a report for the current session or the newest saved TYN session. */
    public static File createReport(Context context, File preferredSessionDir) throws IOException {
        File sessionDir = preferredSessionDir != null && preferredSessionDir.isDirectory()
            ? preferredSessionDir
            : latestSession(context);
        if (sessionDir == null) return null;

        File startupLog = new File(sessionDir, SESSION_LOG_NAME);
        File rubyLog = new File(sessionDir, RUBY_LOG_NAME);
        if (!startupLog.isFile() && !rubyLog.isFile()) return null;

        File exportDir = new File(context.getCacheDir(), "tyranor-diagnostics");
        if (!exportDir.isDirectory() && !exportDir.mkdirs()) {
            throw new IOException("Could not create diagnostics export directory");
        }
        File report = new File(exportDir, "TYN-RGSS-diagnostics.txt");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(report, false), StandardCharsets.UTF_8))) {
            writer.write("TYN RGSS diagnostic report\n");
            writer.write("Exported: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.ROOT).format(new Date()) + "\n");
            writer.write("Session: " + safe(sessionDir.getName()) + "\n\n");
            appendReportSection(writer, "Runtime and device", startupLog);
            appendReportSection(writer, "Ruby / game error log", rubyLog);
        }
        return report;
    }

    public static void share(Activity activity, File report, String chooserTitle) throws IOException {
        Uri uri = FileProvider.getUriForFile(
            activity,
            activity.getPackageName() + ".diagnostics",
            report
        );
        Intent send = new Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "TYN RGSS diagnostics")
            .putExtra(Intent.EXTRA_TEXT, "TYN RGSS runtime diagnostic log")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(activity.getContentResolver(), "TYN RGSS diagnostics", uri));
        activity.startActivity(Intent.createChooser(send, chooserTitle));
    }

    private static File latestSession(Context context) {
        File sessions = new File(context.getFilesDir(), "tyranor/sessions");
        File[] children = sessions.listFiles(File::isDirectory);
        if (children == null) return null;
        File latest = null;
        for (File child : children) {
            if (latest == null || child.lastModified() > latest.lastModified()) latest = child;
        }
        return latest;
    }

    private static void appendReportSection(BufferedWriter writer, String title, File source) throws IOException {
        writer.write("===== " + title + " =====\n");
        if (!source.isFile()) {
            writer.write("No log was produced for this section.\n\n");
            return;
        }
        int total = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                total += line.length() + 1;
                if (total > MAX_REPORT_BYTES) {
                    writer.write("\n[Report truncated at the size limit]\n");
                    break;
                }
                writer.write(line);
                writer.newLine();
            }
        }
        writer.write("\n");
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
}
