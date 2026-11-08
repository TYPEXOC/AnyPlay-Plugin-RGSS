package com.hatkid.mkxpz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decides, for an RGSS1 (RPG Maker XP) game, whether it needs the legacy runtime (r18,
 * Ruby 1.8 behaviour) or a clean modern runtime. RuntimeDetector only says "this is an
 * XP game"; that is not enough, because modern Pokemon Essentials (v20, v21) also use the
 * XP file format but are written for Ruby 3.
 *
 * Pure Java (no Android classes) so it can be tested on a normal JDK, see tests/runtime-detect/.
 *
 * Evidence, strongest first:
 *   1. The Essentials version written inside Data/Scripts.rxdata
 *        ESSENTIALS_VERSION = "16.2"  /  Essentials::VERSION = "21.1"
 *      version <= 18 -> legacy (Ruby 1.8 era), version >= 19 -> modern.
 *      Scripts.rxdata is a Marshal array of [id, name, zlib(code)]. Each code block is a
 *      plain zlib stream, so we find the streams by their header and inflate them.
 *   2. The Windows executable's ProductVersion (the same rule the Kirin reference app
 *      uses): "1", "1.x" or "1,x" -> legacy, any other value -> clean.
 *   3. Nothing found -> clean (the safe default).
 *
 * Every step is bounded (file sizes, inflated bytes) and never throws.
 */
final class RuntimeProbe
{
    /** legacy = needs the Ruby 1.8 compatible runtime. reason = short ASCII text for the log. */
    static final class Verdict
    {
        final boolean legacy;
        final String reason;

        Verdict(boolean legacy, String reason)
        {
            this.legacy = legacy;
            this.reason = reason;
        }
    }

    /** Result of scanning Scripts.rxdata. major 0 = nothing found, 99 = modern, number unknown. */
    static final class Essentials
    {
        final int major;
        final String label;   // e.g. "16.2" (for the log)
        final String note;    // what happened, for the log

        Essentials(int major, String label, String note)
        {
            this.major = major;
            this.label = label;
            this.note = note;
        }
    }

    /** Essentials up to this major version runs on Ruby 1.8 (RPG Maker XP). Newer = modern. */
    static final int LAST_LEGACY_ESSENTIALS = 18;

    private static final int MODERN_UNKNOWN = 99;

    private static final int MAX_SCRIPTS_BYTES = 40 * 1024 * 1024;
    private static final int MAX_SECTION_BYTES = 8 * 1024 * 1024;
    private static final long MAX_TOTAL_INFLATED = 128L * 1024L * 1024L;
    private static final int EXE_CHUNK = 1024 * 1024;

    private static final Pattern ESS_OLD = Pattern.compile(
            "\\bESSENTIALS_?VERSION\\s*=\\s*[\"'](\\d+)(?:\\.(\\d+))?");
    private static final Pattern ESS_MODULE = Pattern.compile(
            "\\bmodule\\s+Essentials\\b[\\s\\S]{0,400}?\\bVERSION\\s*=\\s*[\"'](\\d+)(?:\\.(\\d+))?");
    private static final Pattern ESS_REF = Pattern.compile("\\bEssentials\\s*::\\s*VERSION\\b");

    private static final byte[] KEY_UTF16 = utf16le("ProductVersion");
    private static final byte[] KEY_ASCII = "ProductVersion".getBytes(StandardCharsets.ISO_8859_1);

    private RuntimeProbe() {}

    // ------------------------------------------------------------------ public

    /** The decision for one RGSS1 game folder. Never null, never throws. */
    static Verdict probeRgss1(File gameDir)
    {
        try {
            return probeRgss1Inner(gameDir);
        } catch (Throwable t) {
            return new Verdict(false, "probe failed (" + t.getClass().getSimpleName() + "), default clean");
        }
    }

    private static Verdict probeRgss1Inner(File gameDir)
    {
        if (gameDir == null || !gameDir.isDirectory()) {
            return new Verdict(false, "no game folder to probe, default clean");
        }
        File[] files = gameDir.listFiles();
        if (files == null) {
            return new Verdict(false, "game folder not readable, default clean");
        }

        // 1. Essentials version inside the scripts
        String essNote;
        File scripts = findScriptsFile(files);
        if (scripts == null) {
            essNote = "no Data/Scripts.rxdata to scan";
        } else {
            Essentials e = scanScriptsFile(scripts);
            if (e.major > 0) {
                if (e.major <= LAST_LEGACY_ESSENTIALS) {
                    return new Verdict(true, "Essentials " + e.label + " in scripts (v" + LAST_LEGACY_ESSENTIALS
                            + " and older = Ruby 1.8 era)");
                }
                return new Verdict(false, "Essentials " + e.label + " in scripts (v" + (LAST_LEGACY_ESSENTIALS + 1)
                        + " and newer = modern Ruby)");
            }
            essNote = e.note;
        }

        // 2. exe ProductVersion (Kirin rule)
        File exe = findGameExecutable(files);
        if (exe == null) {
            return new Verdict(false, "no Essentials version (" + essNote + "), no .exe, default clean");
        }
        String pv = readExeProductVersion(exe);
        if (pv == null) {
            return new Verdict(false, "no Essentials version (" + essNote + "), " + exe.getName()
                    + " has no readable ProductVersion, default clean");
        }
        if (isLegacyProductVersion(pv)) {
            return new Verdict(true, exe.getName() + " ProductVersion=" + printable(pv)
                    + " (1.x = legacy), no Essentials version (" + essNote + ")");
        }
        return new Verdict(false, exe.getName() + " ProductVersion=" + printable(pv)
                + " (not 1.x = clean), no Essentials version (" + essNote + ")");
    }

    /** "1", "1.x", "1,x" -> true (same rule as the Kirin reference app). */
    static boolean isLegacyProductVersion(String version)
    {
        if (version == null) return false;
        String v = version.trim().replaceAll("\\s+", "");
        return v.equals("1") || v.startsWith("1.") || v.startsWith("1,");
    }

    // ------------------------------------------------------- Scripts.rxdata scan

    static File findScriptsFile(File[] gameFiles)
    {
        File data = null;
        for (File f : gameFiles) {
            if (f.getName().equalsIgnoreCase("Data") && f.isDirectory()) { data = f; break; }
        }
        if (data == null) return null;
        File[] inside = data.listFiles();
        if (inside == null) return null;
        for (File f : inside) {
            if (f.getName().equalsIgnoreCase("Scripts.rxdata") && f.isFile()) return f;
        }
        return null;
    }

    static Essentials scanScriptsFile(File scripts)
    {
        long len = scripts.length();
        if (len <= 0) return new Essentials(0, "", "Scripts.rxdata is empty");
        if (len > MAX_SCRIPTS_BYTES) {
            return new Essentials(0, "", "Scripts.rxdata too large to scan (" + (len >> 20) + " MB)");
        }
        byte[] data = readAll(scripts, (int) len);
        if (data == null) return new Essentials(0, "", "Scripts.rxdata not readable");
        return scanScriptsBytes(data);
    }

    /** Inflates every zlib stream in a Scripts.rxdata image and looks for an Essentials version. */
    static Essentials scanScriptsBytes(byte[] data)
    {
        int best = 0;
        String bestLabel = "";
        int sections = 0;
        long total = 0;

        int i = 0;
        while (i + 2 < data.length) {
            int b0 = data[i] & 0xff;
            int b1 = data[i + 1] & 0xff;
            boolean zlibHeader = b0 == 0x78
                    && (b1 == 0x01 || b1 == 0x5e || b1 == 0x9c || b1 == 0xda)
                    && ((b0 << 8) | b1) % 31 == 0;
            if (!zlibHeader) { i++; continue; }

            int[] used = new int[1];
            byte[] text = inflateOne(data, i, used);
            if (text == null) { i++; continue; }   // false alarm, keep scanning

            sections++;
            total += text.length;
            Essentials e = findVersion(text);
            if (e != null && e.major > best) {
                best = e.major;
                bestLabel = e.label;
                // A definite answer ends the scan early.
                break;
            }
            i += Math.max(used[0], 1);
            if (total > MAX_TOTAL_INFLATED) break;
        }

        if (best > 0) return new Essentials(best, bestLabel, "found in " + sections + " scripts");
        return new Essentials(0, "", "scanned " + sections + " scripts, no Essentials version constant");
    }

    /** Looks for an Essentials version constant in one script's text. null = none. */
    static Essentials findVersion(byte[] text)
    {
        String s = new String(text, StandardCharsets.ISO_8859_1);
        if (s.indexOf("VERSION") < 0) return null;

        int bestMajor = 0;
        String bestLabel = "";

        Matcher m = ESS_MODULE.matcher(s);
        while (m.find()) {
            int major = parseInt(m.group(1));
            if (major > bestMajor) { bestMajor = major; bestLabel = label(m); }
        }
        m = ESS_OLD.matcher(s);
        while (m.find()) {
            int major = parseInt(m.group(1));
            if (major > bestMajor) { bestMajor = major; bestLabel = label(m); }
        }
        if (bestMajor == 0 && ESS_REF.matcher(s).find()) {
            // Essentials::VERSION is only used by the modern codebase.
            return new Essentials(MODERN_UNKNOWN, "(Essentials::VERSION)", "");
        }
        if (bestMajor == 0) return null;
        return new Essentials(bestMajor, bestLabel, "");
    }

    private static String label(Matcher m)
    {
        String minor = m.group(2);
        return minor == null ? m.group(1) : m.group(1) + "." + minor;
    }

    private static int parseInt(String s)
    {
        if (s == null || s.length() > 4) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    /** Inflates the zlib stream starting at data[off]. null if it is not a complete valid stream. */
    private static byte[] inflateOne(byte[] data, int off, int[] consumed)
    {
        Inflater inf = new Inflater();
        try {
            inf.setInput(data, off, data.length - off);
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[16 * 1024];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) return null;
                    continue;
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_SECTION_BYTES) return null;
            }
            consumed[0] = inf.getTotalIn();
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
    }

    // ------------------------------------------------------ exe ProductVersion

    /**
     * Same order as the Kirin reference app: the executable named like the .ini, then
     * Game.exe, then the first visible .exe (sorted by name). null when there is no .exe.
     */
    static File findGameExecutable(File[] gameFiles)
    {
        File[] sorted = gameFiles.clone();
        Arrays.sort(sorted, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        // .exe that has an .ini with the same base name
        for (File ini : sorted) {
            String n = ini.getName();
            if (!ini.isFile() || !n.toLowerCase(Locale.ROOT).endsWith(".ini")) continue;
            String base = n.substring(0, n.length() - 4);
            for (File exe : sorted) {
                if (exe.isFile() && exe.getName().equalsIgnoreCase(base + ".exe")) return exe;
            }
        }
        // Game.exe
        for (File f : sorted) {
            if (f.isFile() && f.getName().equalsIgnoreCase("Game.exe")) return f;
        }
        // first visible .exe
        for (File f : sorted) {
            String n = f.getName();
            if (f.isFile() && !n.startsWith(".") && n.toLowerCase(Locale.ROOT).endsWith(".exe")) return f;
        }
        return null;
    }

    static String readExeProductVersion(File exe)
    {
        return readExeProductVersion(exe, EXE_CHUNK);
    }

    /** chunk is a parameter only so the tests can force a value to straddle two reads. */
    static String readExeProductVersion(File exe, int chunk)
    {
        String v = findProductVersion(exe, chunk, true);
        if (v == null) v = findProductVersion(exe, chunk, false);
        return v;
    }

    /**
     * Streams through the file and returns the value after the first "ProductVersion" key.
     * utf16: the Windows version resource stores key and value as UTF-16LE.
     */
    private static String findProductVersion(File exe, int chunk, boolean utf16)
    {
        final byte[] key = utf16 ? KEY_UTF16 : KEY_ASCII;
        final int overlap = key.length + 128;   // key + padding + value must fit in one window
        InputStream in = null;
        try {
            in = new FileInputStream(exe);
            byte[] buf = new byte[chunk + overlap];
            int keep = 0;
            while (true) {
                int n = in.read(buf, keep, chunk);
                boolean eof = n <= 0;
                int have = keep + Math.max(n, 0);
                // start positions whose whole window is available (or everything at the end)
                int limit = eof ? have - key.length + 1 : have - overlap;
                for (int i = 0; i < limit; i++) {
                    if (!matches(buf, i, key)) continue;
                    String v = utf16 ? readUtf16Value(buf, i + key.length, have)
                                     : readAsciiValue(buf, i + key.length, have);
                    if (v != null) return v;
                }
                if (eof) return null;
                int carry = Math.min(overlap, have);
                System.arraycopy(buf, have - carry, buf, 0, carry);
                keep = carry;
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static boolean matches(byte[] buf, int at, byte[] key)
    {
        if (at + key.length > buf.length) return false;
        for (int j = 0; j < key.length; j++) {
            if (buf[at + j] != key[j]) return false;
        }
        return true;
    }

    /** After the key: NUL terminator and padding, then the value as UTF-16LE until NUL. */
    private static String readUtf16Value(byte[] b, int p, int end)
    {
        while (p < end && b[p] == 0) p++;
        StringBuilder sb = new StringBuilder();
        while (p + 1 < end && b[p + 1] == 0) {
            int c = b[p] & 0xff;
            if (c < 0x20 || c > 0x7e) break;
            sb.append((char) c);
            p += 2;
            if (sb.length() > 40) break;
        }
        String v = sb.toString().trim();
        return v.isEmpty() ? null : v;
    }

    /** ANSI fallback. Only accepts something that looks like a version number. */
    private static String readAsciiValue(byte[] b, int p, int end)
    {
        while (p < end && b[p] == 0) p++;
        StringBuilder sb = new StringBuilder();
        while (p < end) {
            int c = b[p] & 0xff;
            if (c < 0x20 || c > 0x7e) break;
            sb.append((char) c);
            p++;
            if (sb.length() > 40) break;
        }
        String v = sb.toString().trim();
        return v.matches("\\d[\\d.,\\s]*") ? v : null;
    }


    // ------------------------------------------------------- folder facts (log)

    /**
     * One short line about what is really inside the game folder, for AnyPlayLog's header.
     * It answers questions like "is there a Game.rgssad?", "does Data/ have Scripts.rxdata?",
     * "is Map003.rxdata there, and with which spelling?". Never throws.
     */
    static String describeFolder(File gameDir)
    {
        try {
            return describeFolderInner(gameDir);
        } catch (Throwable t) {
            return "folder facts unavailable (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static String describeFolderInner(File gameDir)
    {
        if (gameDir == null || !gameDir.isDirectory()) return "folder facts: game folder missing";
        File[] files = gameDir.listFiles();
        if (files == null) return "folder facts: game folder not readable";

        File[] sorted = files.clone();
        Arrays.sort(sorted, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        StringBuilder top = new StringBuilder();
        StringBuilder archives = new StringBuilder();
        File data = null;
        int shown = 0;
        for (File f : sorted) {
            String n = f.getName();
            String lower = n.toLowerCase(Locale.ROOT);
            if (lower.equals("data") && f.isDirectory()) data = f;
            if (lower.endsWith(".rgssad") || lower.endsWith(".rgss2a") || lower.endsWith(".rgss3a")
                    || lower.endsWith(".kpak")) {
                if (archives.length() > 0) archives.append(',');
                archives.append(printable(n)).append(' ').append(f.length() >> 20).append("MB");
            }
            if (shown < 12) {
                if (top.length() > 0) top.append(',');
                top.append(printable(n)).append(f.isDirectory() ? "/" : "");
                shown++;
            }
        }

        StringBuilder sb = new StringBuilder("folder facts: ");
        sb.append(files.length).append(" entries [").append(top);
        if (files.length > shown) sb.append(",...");
        sb.append("]; archives=").append(archives.length() == 0 ? "none" : archives.toString());

        if (data == null) {
            sb.append("; no Data/ folder");
            return sb.toString();
        }
        File[] inside = data.listFiles();
        if (inside == null) {
            sb.append("; Data/ not readable");
            return sb.toString();
        }
        int maps = 0;
        int odd = 0;           // names that do not have the usual spelling ("Map003.rxdata")
        boolean scripts = false;
        String m003 = "no", m178 = "no";
        for (File f : inside) {
            String n = f.getName();
            String lower = n.toLowerCase(Locale.ROOT);
            if (lower.equals("scripts.rxdata")) scripts = true;
            if (lower.matches("map\\d+\\.rxdata")) {
                maps++;
                if (!n.matches("Map\\d+\\.rxdata")) odd++;
            }
            if (lower.equals("map003.rxdata")) m003 = n.equals("Map003.rxdata") ? "yes" : "as " + printable(n);
            if (lower.equals("map178.rxdata")) m178 = n.equals("Map178.rxdata") ? "yes" : "as " + printable(n);
        }
        sb.append("; Data/ has ").append(inside.length).append(" files, ").append(maps).append(" Map*.rxdata");
        if (odd > 0) sb.append(" (").append(odd).append(" with unusual spelling)");
        sb.append(", Scripts.rxdata=").append(scripts ? "yes" : "no");
        sb.append(", Map003=").append(m003).append(", Map178=").append(m178);
        return sb.toString();
    }

    // ----------------------------------------------------------------- helpers

    private static byte[] utf16le(String s)
    {
        byte[] out = new byte[s.length() * 2];
        for (int i = 0; i < s.length(); i++) {
            out[2 * i] = (byte) s.charAt(i);
            out[2 * i + 1] = 0;
        }
        return out;
    }

    private static byte[] readAll(File f, int expected)
    {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] data = new byte[expected];
            int off = 0;
            while (off < expected) {
                int n = in.read(data, off, expected - off);
                if (n < 0) break;
                off += n;
            }
            if (off == expected) return data;
            return Arrays.copyOf(data, off);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** Keeps log text plain ASCII on one line. */
    private static String printable(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 40; i++) {
            char c = s.charAt(i);
            sb.append(c >= 0x20 && c <= 0x7e ? c : '?');
        }
        return sb.toString();
    }
}
