package com.hatkid.mkxpz;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import com.tyranor.runtime.diagnostics.TynRuntimeDiagnostics;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Streams a granted translation JSON URI into mkxp-z's native in-memory index. */
final class RgssTranslationSession implements AutoCloseable {
    private static final String TAG = "mkxp-z[Translation]";
    private static final String REVISION_COLUMN = "revision";
    private static final long REVISION_HEARTBEAT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long NOTIFICATION_COALESCE_MS = 120;
    private static final long INITIAL_RETRY_MS = 1_000;
    private static final long MAX_RETRY_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int MAX_TRANSLATION_BYTES = 64 * 1024 * 1024;
    private static final int CHUNK_TARGET_BYTES = 256 * 1024;
    private static final Charset[] CHARSETS = {
        StandardCharsets.UTF_8,
        Charset.forName("Shift_JIS"),
        Charset.forName("windows-1252"),
        Charset.forName("windows-1250"),
        Charset.forName("windows-1251"),
        Charset.forName("GB18030")
    };

    private final ContentResolver resolver;
    private final Uri uri;
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
        append("Coalesced translation URI notification; streaming the map into the native index");
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

    RgssTranslationSession(ContentResolver resolver, Uri uri, File sessionDir) {
        this.resolver = resolver;
        this.uri = uri;
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
            append("Translation URI observer registered before the initial native table load");
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
                return refreshTable("initial load");
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
            + "; streaming the updated map");
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

    /** Builds a replacement table off-thread; native lookups keep using the old table until commit. */
    private boolean refreshTable(String trigger) {
        if (closed) return false;
        boolean staging = false;
        try {
            if (!MainActivity.nativeBeginTranslationUpdate()) {
                throw new IOException("Native translation index could not begin an update");
            }
            staging = true;
            ChunkWriter chunks = new ChunkWriter();
            VariantWorkspace variants = new VariantWorkspace();
            long recordCount = 0;
            try (InputStream raw = resolver.openInputStream(uri)) {
                if (raw == null) throw new IOException("Translation provider returned no input stream");
                BoundedInputStream bounded = new BoundedInputStream(raw, MAX_TRANSLATION_BYTES);
                try (JsonReader json = new JsonReader(stripOptionalBom(bounded))) {
                    json.setLenient(false);
                    if (json.peek() != JsonToken.BEGIN_OBJECT) {
                        throw new IOException("Translation map root must be a JSON object");
                    }
                    json.beginObject();
                    while (json.hasNext()) {
                        String source = json.nextName();
                        if (json.peek() == JsonToken.STRING) {
                            String target = json.nextString();
                            recordCount += appendTranslation(source, target, chunks, variants);
                        } else {
                            json.skipValue();
                        }
                    }
                    json.endObject();
                    if (json.peek() != JsonToken.END_DOCUMENT) {
                        throw new IOException("Unexpected content after the translation map");
                    }
                }
            }
            chunks.flush();
            if (!MainActivity.nativeCommitTranslationUpdate()) {
                throw new IOException("Native translation index rejected the completed update (memory cap or allocation failure)");
            }
            staging = false;
            hasSuccessfulLoad = true;
            nextRetryMs = INITIAL_RETRY_MS;
            cancelRetry();
            append("Translation table committed from " + trigger + "; byteLimit="
                + MAX_TRANSLATION_BYTES + ", nativeRecords=" + recordCount
                + ", revision=" + MainActivity.nativeTranslationTableRevision());
            return true;
        } catch (Exception error) {
            if (staging) MainActivity.nativeAbortTranslationUpdate();
            Log.w(TAG, "Translation table stream or native publish failed (" + trigger + ")", error);
            append("Translation table update failed; the previous in-memory table remains active", error);
            scheduleRetry();
            return false;
        }
    }

    private static Reader stripOptionalBom(InputStream input) throws IOException {
        PushbackReader reader = new PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != -1 && first != '\uFEFF') reader.unread(first);
        return reader;
    }

    private static long appendTranslation(
        String source,
        String target,
        ChunkWriter chunks,
        VariantWorkspace variants
    ) throws IOException {
        if (source.isEmpty() || target.isEmpty()) return 0;
        variants.clear();
        for (int encoding = 0; encoding < CHARSETS.length; encoding++) {
            Charset charset = CHARSETS[encoding];
            byte[] sourceBytes = encodeStrict(source, charset);
            if (sourceBytes == null || sourceBytes.length == 0) continue;
            byte[] targetBytes = encodeStrict(target, charset);
            int targetEncoding = encoding;
            if (targetBytes == null) {
                targetEncoding = 0;
                targetBytes = target.getBytes(StandardCharsets.UTF_8);
            }

            variants.add(sourceBytes, targetBytes, targetEncoding);
        }

        for (int i = 0; i < variants.size; i++) {
            chunks.write(variants.keys[i], variants.values[i], variants.encodings[i]);
        }
        return variants.size;
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

    private void scheduleRetry() {
        if (closed) return;
        synchronized (taskLock) {
            if (retryFuture != null && !retryFuture.isDone()) return;
            long delay = nextRetryMs;
            nextRetryMs = Math.min(nextRetryMs * 2, MAX_RETRY_MS);
            append("Translation table stream failed; keeping the previous in-memory table and retrying in " + delay + "ms");
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

    private static final class VariantWorkspace {
        final byte[][] keys = new byte[CHARSETS.length][];
        final byte[][] values = new byte[CHARSETS.length][];
        final int[] encodings = new int[CHARSETS.length];
        int size;

        void clear() {
            size = 0;
        }

        void add(byte[] key, byte[] value, int encoding) {
            for (int i = 0; i < size; i++) {
                if (Arrays.equals(keys[i], key)) {
                    keys[i] = key;
                    values[i] = value;
                    encodings[i] = encoding;
                    return;
                }
            }
            keys[size] = key;
            values[size] = value;
            encodings[size] = encoding;
            size++;
        }
    }

    private final class ChunkWriter {
        private ByteArrayOutputStream bytes = new ByteArrayOutputStream(CHUNK_TARGET_BYTES);
        private DataOutputStream output = new DataOutputStream(bytes);

        void write(byte[] key, byte[] value, int encoding) throws IOException {
            output.writeInt(key.length);
            output.writeInt(value.length);
            output.writeByte(encoding);
            output.write(key);
            output.write(value);
            if (bytes.size() >= CHUNK_TARGET_BYTES) flush();
        }

        void flush() throws IOException {
            if (bytes.size() == 0) return;
            if (!MainActivity.nativeAppendTranslationChunk(bytes.toByteArray())) {
                throw new IOException("Native translation index rejected a chunk (64 MiB source cap or 192 MiB index cap)");
            }
            bytes = new ByteArrayOutputStream(CHUNK_TARGET_BYTES);
            output = new DataOutputStream(bytes);
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long bytesRead;

        BoundedInputStream(InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maxBytes) {
                int extra = in.read();
                if (extra == -1) return -1;
                throw new IOException("Translation JSON exceeds " + maxBytes + " bytes");
            }
            int value = in.read();
            if (value != -1) bytesRead++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            long remaining = maxBytes - bytesRead;
            if (remaining <= 0) {
                int extra = in.read();
                if (extra == -1) return -1;
                throw new IOException("Translation JSON exceeds " + maxBytes + " bytes");
            }
            int count = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (count > 0) bytesRead += count;
            return count;
        }
    }

}
