package com.hatkid.mkxpz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds out which RGSS generation a game folder belongs to (1 = XP, 2 = VX, 3 = VX Ace).
 *
 * Pure Java (no Android classes) so it can be tested on a normal JDK, see
 * tests/runtime-detect/. MainActivity maps the result to a runtime.
 *
 * The signals, strongest first. The first four follow what the mkxp-z engine itself
 * does, so the runtime we pick always agrees with the RGSS version the engine will use:
 *   1. mkxp.json   "rgssVersion": 1..3     (0 / missing = not set)
 *   2. Game.ini    Scripts=...             (.rxdata = 1, .rvdata = 2, .rvdata2 = 3)
 *   3. Game.ini    Library=RGSS1xx.dll     (the digit after "RGSS")
 *   4. Data/Scripts.rxdata | .rvdata | .rvdata2 on disk
 *   5. Game.rgssad | .rgss2a | .rgss3a      (encrypted archive next to the game)
 * The Windows executable (ProductVersion) is NOT used: it says nothing about RGSS.
 *
 * File names are matched case-insensitively because Android's file system is case
 * sensitive while the Windows games were not ("data" vs "Data", "game.ini" vs "Game.ini").
 */
final class RuntimeDetector
{
    /** rgss: 1, 2, 3, or 0 when nothing could be determined. reason: short ASCII text for logs. */
    static final class Detection
    {
        final int rgss;
        final String reason;

        Detection(int rgss, String reason)
        {
            this.rgss = rgss;
            this.reason = reason;
        }
    }

    /** Name of the optional per-game override file in the game folder. */
    static final String OVERRIDE_FILE = "anyplay_runtime.txt";

    private static final int MAX_TEXT_BYTES = 256 * 1024;

    private static final Pattern JSON_RGSS = Pattern.compile("\"rgssVersion\"\\s*:\\s*(\\d+)");
    private static final Pattern JSON_EXEC = Pattern.compile("\"execName\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LIB_RGSS = Pattern.compile("rgss\\s*(\\d)", Pattern.CASE_INSENSITIVE);

    private RuntimeDetector() {}

    // ------------------------------------------------------------------ public

    static Detection detect(File gameDir)
    {
        if (gameDir == null || !gameDir.isDirectory()) {
            return new Detection(0, "game folder missing");
        }
        File[] files = gameDir.listFiles();
        if (files == null) {
            return new Detection(0, "game folder not readable (storage permission?)");
        }

        // Every signal found is recorded, so a disagreement shows up in the log.
        int fromJson = 0, fromScripts = 0, fromLibrary = 0, fromData = 0, fromArchive = 0;
        String jsonText = "", scriptsText = "", libraryText = "", dataText = "", archiveText = "";

        // 1. mkxp.json
        String execName = "Game";
        File json = findIgnoreCase(files, "mkxp.json");
        if (json != null) {
            String text = stripJsonComments(readText(json));
            Matcher m = JSON_EXEC.matcher(text);
            if (m.find() && !m.group(1).trim().isEmpty()) execName = m.group(1).trim();
            m = JSON_RGSS.matcher(text);
            if (m.find()) {
                int v = parseSmallInt(m.group(1));
                if (v >= 1 && v <= 3) {
                    fromJson = v;
                    jsonText = "mkxp.json rgssVersion=" + v;
                }
            }
        }

        // 2./3. Game.ini (the engine reads <execName>.ini)
        File ini = findIgnoreCase(files, execName + ".ini");
        if (ini != null) {
            String scripts = null, library = null;
            for (String line : readText(ini).split("\r?\n|\r")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = line.substring(eq + 1).trim();
                if (key.equals("scripts") && scripts == null) scripts = val;
                else if (key.equals("library") && library == null) library = val;
            }
            if (scripts != null && !scripts.isEmpty()) {
                int v = rgssFromScriptsName(scripts);
                if (v != 0) {
                    fromScripts = v;
                    scriptsText = ini.getName() + " Scripts=" + printable(scripts);
                }
            }
            if (library != null && !library.isEmpty()) {
                int v = rgssFromLibraryName(library);
                if (v != 0) {
                    fromLibrary = v;
                    libraryText = ini.getName() + " Library=" + printable(library);
                }
            }
        }

        // 4. Data/Scripts.*
        File dataDir = findIgnoreCase(files, "Data");
        if (dataDir != null && dataDir.isDirectory()) {
            File[] dataFiles = dataDir.listFiles();
            if (dataFiles != null) {
                for (File f : dataFiles) {
                    int v = rgssFromScriptsFile(f.getName());
                    if (v != 0) {
                        fromData = v;
                        dataText = dataDir.getName() + "/" + f.getName();
                        break;
                    }
                }
            }
        }

        // 5. encrypted archive
        for (File f : files) {
            int v = rgssFromArchiveName(f.getName());
            if (v != 0 && f.isFile()) {
                fromArchive = v;
                archiveText = f.getName();
                break;
            }
        }

        int rgss = 0;
        String used = "";
        if (fromJson != 0) { rgss = fromJson; used = jsonText; }
        else if (fromScripts != 0) { rgss = fromScripts; used = scriptsText; }
        else if (fromLibrary != 0) { rgss = fromLibrary; used = libraryText; }
        else if (fromData != 0) { rgss = fromData; used = dataText; }
        else if (fromArchive != 0) { rgss = fromArchive; used = archiveText; }

        if (rgss == 0) {
            return new Detection(0, "no RGSS signal found (no mkxp.json rgssVersion, no Game.ini Scripts/Library,"
                    + " no Data/Scripts.*, no archive)");
        }

        // Disagreements between the other signals and the one that was used.
        List<String> conflicts = new ArrayList<String>();
        addConflict(conflicts, rgss, fromScripts, scriptsText);
        addConflict(conflicts, rgss, fromLibrary, libraryText);
        addConflict(conflicts, rgss, fromData, dataText);
        addConflict(conflicts, rgss, fromArchive, archiveText);
        if (fromJson != 0) addConflict(conflicts, rgss, fromJson, jsonText);

        StringBuilder sb = new StringBuilder();
        sb.append("RGSS").append(rgss).append(" via ").append(used);
        if (!conflicts.isEmpty()) {
            sb.append(" [other signals disagree: ");
            for (int i = 0; i < conflicts.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(conflicts.get(i));
            }
            sb.append("]");
        }
        return new Detection(rgss, sb.toString());
    }

    /**
     * Content of the per-game override file (first non-empty line that does not start
     * with '#'), lower-cased and trimmed, or null when there is no such file / line.
     * The caller decides which values it understands.
     */
    static String readOverride(File gameDir)
    {
        if (gameDir == null) return null;
        File[] files = gameDir.listFiles();
        if (files == null) return null;
        File f = findIgnoreCase(files, OVERRIDE_FILE);
        if (f == null || !f.isFile()) return null;
        for (String line : readText(f).split("\r?\n|\r")) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            if (s.length() > 32) s = s.substring(0, 32);
            return s.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    // --------------------------------------------------- name -> RGSS (testable)

    /** "Data\\Scripts.rxdata" -> 1, ".rvdata" -> 2, ".rvdata2" -> 3, anything else 0. */
    static int rgssFromScriptsName(String value)
    {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.endsWith(".rvdata2")) return 3;
        if (v.endsWith(".rvdata")) return 2;
        if (v.endsWith(".rxdata")) return 1;
        return 0;
    }

    /** "System\\RGSS102E.dll" -> 1, "RGSS202J.dll" -> 2, "RGSS301.dll" -> 3, else 0. */
    static int rgssFromLibraryName(String value)
    {
        String v = value.trim();
        int cut = Math.max(v.lastIndexOf('\\'), v.lastIndexOf('/'));
        if (cut >= 0) v = v.substring(cut + 1);
        Matcher m = LIB_RGSS.matcher(v);
        if (!m.find()) return 0;
        int d = m.group(1).charAt(0) - '0';
        return (d >= 1 && d <= 3) ? d : 0;
    }

    /** Only the exact names the RPG Maker editors create (Scripts.rxdata etc.). */
    static int rgssFromScriptsFile(String name)
    {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("scripts.rxdata")) return 1;
        if (n.equals("scripts.rvdata")) return 2;
        if (n.equals("scripts.rvdata2")) return 3;
        return 0;
    }

    static int rgssFromArchiveName(String name)
    {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".rgssad")) return 1;
        if (n.endsWith(".rgss2a")) return 2;
        if (n.endsWith(".rgss3a")) return 3;
        return 0;
    }

    // ----------------------------------------------------------------- helpers

    private static void addConflict(List<String> out, int used, int other, String text)
    {
        if (other != 0 && other != used) out.add(text + " says RGSS" + other);
    }

    private static int parseSmallInt(String s)
    {
        if (s == null || s.length() > 3) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static File findIgnoreCase(File[] files, String name)
    {
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    /** Keeps log text plain ASCII on one line. */
    private static String printable(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 80; i++) {
            char c = s.charAt(i);
            sb.append(c >= 0x20 && c <= 0x7e ? c : '?');
        }
        return sb.toString();
    }

    private static String stripJsonComments(String s)
    {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\r\\n]*", "");
    }

    /** Reads up to MAX_TEXT_BYTES, decoding UTF-16 (BOM) / UTF-8 (BOM) / plain bytes. */
    private static String readText(File f)
    {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < MAX_TEXT_BYTES) out.write(buf, 0, n);
            byte[] b = out.toByteArray();
            Charset cs = StandardCharsets.ISO_8859_1; // keeps every ASCII byte intact
            int off = 0;
            if (b.length >= 2 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xFE) { cs = StandardCharsets.UTF_16LE; off = 2; }
            else if (b.length >= 2 && (b[0] & 0xff) == 0xFE && (b[1] & 0xff) == 0xFF) { cs = StandardCharsets.UTF_16BE; off = 2; }
            else if (b.length >= 3 && (b[0] & 0xff) == 0xEF && (b[1] & 0xff) == 0xBB && (b[2] & 0xff) == 0xBF) { off = 3; }
            return new String(b, off, b.length - off, cs);
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }
}
