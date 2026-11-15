package com.hatkid.mkxpz;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.util.Log;

import com.tyranor.runtime.diagnostics.TynRuntimeDiagnostics;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Keeps the Ruby translation table in sync with one TYN-granted URI. */
final class RgssTranslationSession implements AutoCloseable {
    private static final String TAG = "mkxp-z[Translation]";
    private static final String REVISION_COLUMN = "revision";
    private static final long REVISION_HEARTBEAT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long NOTIFICATION_COALESCE_MS = 120;
    private static final long INITIAL_RETRY_MS = 1_000;
    private static final long MAX_RETRY_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int MAX_TRANSLATION_BYTES = 16 * 1024 * 1024;

    private final ContentResolver resolver;
    private final Uri uri;
    private final File destination;
    private final File sessionDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RGSS-translation-session");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;
    private volatile boolean started;
    private volatile boolean hasSuccessfulLoad;
    private final Object taskLock = new Object();
    private final Runnable notificationRefresh = () -> {
        if (closed) return;
        cancelRetry();
        append("Coalesced translation URI notification; re-reading the map");
        try {
            worker.execute(() -> refreshTable("URI notification"));
        } catch (RejectedExecutionException ignored) {
            // Closing the session may race a queued ContentObserver callback.
        }
    };
    private final ContentObserver observer = new ContentObserver(mainHandler) {
        @Override
        public void onChange(boolean selfChange) {
            onUriChanged(null);
        }

        @Override
        public void onChange(boolean selfChange, Uri changedUri) {
            onUriChanged(changedUri);
        }
    };

    private boolean observerRegistered;
    private long lastRevision;
    private boolean hasRevision;
    private long nextRetryMs = INITIAL_RETRY_MS;
    private ScheduledFuture<?> retryFuture;
    private ScheduledFuture<?> heartbeatFuture;

    RgssTranslationSession(ContentResolver resolver, Uri uri, File destination, File sessionDir) {
        this.resolver = resolver;
        this.uri = uri;
        this.destination = destination;
        this.sessionDir = sessionDir;
    }

    /** Registers first, then completes the cold load before the native game thread starts. */
    synchronized boolean start() {
        if (closed) return false;
        if (started) return hasSuccessfulLoad;
        started = true;

        try {
            resolver.registerContentObserver(uri, false, observer);
            observerRegistered = true;
            append("Translation URI observer registered before the initial table read");
        } catch (Exception error) {
            Log.w(TAG, "Could not register the translation URI observer", error);
            append("Translation URI observer registration failed; revision heartbeat remains enabled", error);
        }

        boolean loaded = false;
        try {
            Future<Boolean> initialLoad = worker.submit(() -> {
                Long revision = readRevision("initial baseline");
                if (revision != null) {
                    lastRevision = revision;
                    hasRevision = true;
                    append("Initial translation revision recorded before table load: " + revision);
                }
                boolean result = refreshTable("initial load");
                return result;
            });
            loaded = initialLoad.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            append("Initial translation table load was interrupted; the session will retry", error);
            scheduleRetry();
        } catch (ExecutionException | RejectedExecutionException error) {
            append("Initial translation table load could not be completed; the session will retry", error);
            scheduleRetry();
        }

        if (!closed) {
            heartbeatFuture = worker.scheduleWithFixedDelay(
                this::checkRevision,
                REVISION_HEARTBEAT_MS,
                REVISION_HEARTBEAT_MS,
                TimeUnit.MILLISECONDS
            );
        }
        return loaded || hasSuccessfulLoad;
    }

    private void onUriChanged(Uri changedUri) {
        if (closed || (changedUri != null && !uri.equals(changedUri))) return;
        append("Received translation URI change notification");
        mainHandler.removeCallbacks(notificationRefresh);
        mainHandler.postDelayed(notificationRefresh, NOTIFICATION_COALESCE_MS);
    }

    private void checkRevision() {
        if (closed) return;
        Long revision = readRevision("heartbeat");
        if (revision == null) {
            if (!hasSuccessfulLoad) scheduleRetry();
            return;
        }

        if (!hasRevision) {
            lastRevision = revision;
            hasRevision = true;
            append("Translation revision heartbeat baseline: " + revision);
            return;
        }

        if (revision == lastRevision) {
            append("Translation revision heartbeat unchanged: " + revision);
            return;
        }

        long previousRevision = lastRevision;
        lastRevision = revision;
        hasRevision = true;
        append("Translation revision changed: " + previousRevision + " -> " + revision
            + "; re-reading the map");
        refreshTable("revision change");
    }

    private Long readRevision(String reason) {
        try (Cursor cursor = resolver.query(uri, new String[] {REVISION_COLUMN}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("Translation provider returned no revision row");
            }
            int column = cursor.getColumnIndexOrThrow(REVISION_COLUMN);
            return cursor.getLong(column);
        } catch (Exception error) {
            Log.w(TAG, "Could not read translation revision (" + reason + ")", error);
            append("Translation revision query failed (" + reason + ")", error);
            return null;
        }
    }

    /** Runs only on the single worker so map reads, comparisons, and writes stay ordered. */
    private boolean refreshTable(String trigger) {
        if (closed) return false;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Translation provider returned no input stream");
            byte[] jsonBytes = readBounded(input);
            JSONObject map = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8).replace("\uFEFF", ""));
            String encoded = encodeTranslationMap(map);
            String current = destination.isFile()
                ? new String(readBytes(destination), StandardCharsets.US_ASCII)
                : null;

            if (!encoded.equals(current)) {
                writeAtomically(destination, encoded);
                append("Translation table updated from " + trigger + "; entries=" + map.length()
                    + ", encodedBytes=" + encoded.length());
            } else {
                append("Translation table unchanged after " + trigger + "; entries=" + map.length());
            }

            hasSuccessfulLoad = true;
            nextRetryMs = INITIAL_RETRY_MS;
            cancelRetry();
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Translation table read or publish failed (" + trigger + ")", error);
            scheduleRetry();
            return false;
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            if (output.size() + count > MAX_TRANSLATION_BYTES) {
                throw new IOException("Translation table exceeds " + MAX_TRANSLATION_BYTES + " bytes");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void scheduleRetry() {
        if (closed) return;
        synchronized (taskLock) {
            if (retryFuture != null && !retryFuture.isDone()) return;
            long delay = nextRetryMs;
            nextRetryMs = Math.min(nextRetryMs * 2, MAX_RETRY_MS);
            append("Translation table read failed; keeping the previous file and retrying in " + delay + "ms");
            try {
                retryFuture = worker.schedule(() -> {
                    synchronized (taskLock) {
                        retryFuture = null;
                    }
                    refreshTable("retry");
                }, delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                retryFuture = null;
            }
        }
    }

    private void cancelRetry() {
        synchronized (taskLock) {
            if (retryFuture != null) {
                retryFuture.cancel(false);
                retryFuture = null;
            }
        }
    }

    private static String encodeTranslationMap(JSONObject map) throws Exception {
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

    private static byte[] encodeStrict(String value, Charset charset) {
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

    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) output.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return output.toString();
    }

    private byte[] readBytes(File file) throws IOException {
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                if (output.size() + count > MAX_TRANSLATION_BYTES) {
                    throw new IOException("Runtime translation table exceeds " + MAX_TRANSLATION_BYTES + " bytes");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void writeAtomically(File destination, String text) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create " + parent);
        }
        File temporary = new File(destination.getAbsolutePath() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
        } catch (Exception error) {
            temporary.delete();
            throw new IOException("Unable to atomically publish " + destination, error);
        }
    }

    private void append(String message) {
        TynRuntimeDiagnostics.append(sessionDir, message);
    }

    private void append(String message, Throwable error) {
        TynRuntimeDiagnostics.append(sessionDir, message, error);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        mainHandler.removeCallbacks(notificationRefresh);
        if (observerRegistered) {
            try {
                resolver.unregisterContentObserver(observer);
                append("Translation URI observer unregistered");
            } catch (Exception error) {
                Log.w(TAG, "Could not unregister the translation URI observer", error);
                append("Translation URI observer release failed", error);
            }
            observerRegistered = false;
        }
        synchronized (taskLock) {
            if (retryFuture != null) retryFuture.cancel(false);
            retryFuture = null;
            if (heartbeatFuture != null) heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
        worker.shutdownNow();
    }

}
