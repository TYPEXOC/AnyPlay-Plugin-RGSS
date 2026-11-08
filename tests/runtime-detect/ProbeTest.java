package com.hatkid.mkxpz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.zip.Deflater;

/** Host test for RuntimeProbe. Run with run.sh. */
public class ProbeTest {
    static int fails = 0, runs = 0;

    static void eq(String name, Object want, Object got) {
        runs++;
        if (!String.valueOf(want).equals(String.valueOf(got))) { fails++; System.out.println("FAIL " + name + ": want " + want + " got " + got); }
        else System.out.println("ok   " + name);
    }

    // ---- fixtures -------------------------------------------------------
    static byte[] utf16(String s) {
        byte[] o = new byte[s.length() * 2];
        for (int i = 0; i < s.length(); i++) { o[2 * i] = (byte) s.charAt(i); }
        return o;
    }

    /** Fake Windows exe: random junk, then a version-resource style "ProductVersion" entry. */
    static byte[] fakeExe(int junkBefore, String version, boolean wide, int padding) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] junk = new byte[junkBefore];
        new Random(7).nextBytes(junk);
        // keep the junk free of an accidental key
        for (int i = 0; i < junk.length; i++) if (junk[i] == 'P') junk[i] = 'Q';
        o.write(junk, 0, junk.length);
        try {
            if (wide) {
                o.write(utf16("ProductVersion")); o.write(0); o.write(0);
                for (int i = 0; i < padding; i++) o.write(0);
                o.write(utf16(version)); o.write(0); o.write(0);
            } else {
                o.write("ProductVersion".getBytes(StandardCharsets.ISO_8859_1)); o.write(0);
                o.write(version.getBytes(StandardCharsets.ISO_8859_1)); o.write(0);
            }
            o.write(new byte[300]);
        } catch (IOException e) { throw new RuntimeException(e); }
        return o.toByteArray();
    }

    static void fixnum(ByteArrayOutputStream o, int n) {
        if (n == 0) { o.write(0); return; }
        if (n > 0 && n < 123) { o.write(n + 5); return; }
        if (n < 0 && n > -124) { o.write((n - 5) & 0xff); return; }
        int bytes = n < 256 ? 1 : n < 65536 ? 2 : n < 16777216 ? 3 : 4;
        o.write(bytes);
        for (int i = 0; i < bytes; i++) o.write((n >> (8 * i)) & 0xff);
    }

    static byte[] zlib(String text, int level) {
        Deflater d = new Deflater(level);
        d.setInput(text.getBytes(StandardCharsets.ISO_8859_1));
        d.finish();
        byte[] buf = new byte[text.length() + 64];
        int n = d.deflate(buf);
        d.end();
        byte[] out = new byte[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    /** Scripts.rxdata layout: Marshal array of [id, name, zlib(code)]. */
    static byte[] scriptsRxdata(String[] names, String[] codes, int level) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(4); o.write(8);
        o.write('['); fixnum(o, codes.length);
        for (int i = 0; i < codes.length; i++) {
            o.write('['); fixnum(o, 3);
            o.write('i'); fixnum(o, 1000 + i);
            byte[] name = names[i].getBytes(StandardCharsets.ISO_8859_1);
            o.write('"'); fixnum(o, name.length); o.write(name, 0, name.length);
            byte[] z = zlib(codes[i], level);
            o.write('"'); fixnum(o, z.length); o.write(z, 0, z.length);
        }
        return o.toByteArray();
    }

    static String filler(int n) {
        StringBuilder sb = new StringBuilder();
        Random r = new Random(3);
        for (int i = 0; i < n; i++) sb.append("def m").append(i).append("(x); x + ").append(r.nextInt(1000)).append("; end\n");
        return sb.toString();
    }

    static File game(Object... spec) throws Exception {   // alternating: "path", byte[] | String
        File root = Files.createTempDirectory("probe").toFile();
        for (int i = 0; i < spec.length; i += 2) {
            File f = new File(root, (String) spec[i]);
            f.getParentFile().mkdirs();
            byte[] data = spec[i + 1] instanceof byte[] ? (byte[]) spec[i + 1]
                    : ((String) spec[i + 1]).getBytes(StandardCharsets.ISO_8859_1);
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
        }
        return root;
    }

    static byte[] scriptsWith(String versionLine) {
        return scriptsRxdata(new String[] { "Main", "Settings", "Other" },
                new String[] { filler(30), "# settings\n" + versionLine + "\n" + filler(10), filler(20) }, 6);
    }

    // ---- tests ----------------------------------------------------------
    public static void main(String[] a) throws Exception {
        // ProductVersion rule (Kirin): 1, 1.x, 1,x = legacy
        eq("pv '1'", true, RuntimeProbe.isLegacyProductVersion("1"));
        eq("pv '1.0.2.0'", true, RuntimeProbe.isLegacyProductVersion("1.0.2.0"));
        eq("pv '1,0,0,0'", true, RuntimeProbe.isLegacyProductVersion("1,0,0,0"));
        eq("pv ' 1 . 0'", true, RuntimeProbe.isLegacyProductVersion(" 1 . 0"));
        eq("pv '10.1'", false, RuntimeProbe.isLegacyProductVersion("10.1"));
        eq("pv '2.4.0'", false, RuntimeProbe.isLegacyProductVersion("2.4.0"));
        eq("pv '12'", false, RuntimeProbe.isLegacyProductVersion("12"));
        eq("pv null", false, RuntimeProbe.isLegacyProductVersion(null));

        // reading ProductVersion from an exe
        File d = game("Game.exe", fakeExe(5000, "1.2.7.1", true, 2));
        eq("exe utf16 padding 2", "1.2.7.1", RuntimeProbe.readExeProductVersion(new File(d, "Game.exe")));
        d = game("Game.exe", fakeExe(5000, "2.4.0", true, 0));
        eq("exe utf16 padding 0", "2.4.0", RuntimeProbe.readExeProductVersion(new File(d, "Game.exe")));
        d = game("Game.exe", fakeExe(3000, "1.0.0.0", false, 0));
        eq("exe ascii fallback", "1.0.0.0", RuntimeProbe.readExeProductVersion(new File(d, "Game.exe")));
        // value straddling chunk boundaries: try many small chunk sizes and junk offsets
        boolean allOk = true;
        for (int junk = 150; junk < 450; junk += 7) {
            File dd = game("Game.exe", fakeExe(junk, "3.1.4.1", true, 2));
            for (int chunk : new int[] { 1, 16, 64, 100, 157, 256 }) {
                String got = RuntimeProbe.readExeProductVersion(new File(dd, "Game.exe"), chunk);
                if (!"3.1.4.1".equals(got)) { allOk = false; System.out.println("   chunk " + chunk + " junk " + junk + " got " + got); }
            }
        }
        eq("exe value across chunk boundaries", true, allOk);
        d = game("Game.exe", new byte[2000]);
        eq("exe without key", "null", String.valueOf(RuntimeProbe.readExeProductVersion(new File(d, "Game.exe"))));
        d = game("Game.exe", "");
        eq("exe empty file", "null", String.valueOf(RuntimeProbe.readExeProductVersion(new File(d, "Game.exe"))));

        // executable choice
        d = game("Other.exe", "x", "Game.exe", "x", "Game.ini", "x");
        eq("exe: Game.exe via Game.ini", "Game.exe", RuntimeProbe.findGameExecutable(d.listFiles()).getName());
        d = game("Alpha.exe", "x", "Zed.exe", "x", "Zed.ini", "x");
        eq("exe: same base name as .ini wins", "Zed.exe", RuntimeProbe.findGameExecutable(d.listFiles()).getName());
        d = game("Alpha.exe", "x", "Game.exe", "x");
        eq("exe: Game.exe without ini", "Game.exe", RuntimeProbe.findGameExecutable(d.listFiles()).getName());
        d = game("beta.exe", "x", "Alpha.EXE", "x");
        eq("exe: first by name", "Alpha.EXE", RuntimeProbe.findGameExecutable(d.listFiles()).getName());
        d = game("readme.txt", "x");
        eq("exe: none", "null", String.valueOf(RuntimeProbe.findGameExecutable(d.listFiles())));
        d = game("GAME.EXE", "x", "game.ini", "x");
        eq("exe: case-insensitive pair", "GAME.EXE", RuntimeProbe.findGameExecutable(d.listFiles()).getName());

        // version regexes
        eq("ver ESSENTIALS_VERSION 16.2", "16", ver("ESSENTIALS_VERSION = \"16.2\""));
        eq("ver ESSENTIALSVERSION 15", "15", ver("ESSENTIALSVERSION=\"15\""));
        eq("ver ESSENTIALS_VERSION 17.2", "17", ver("  ESSENTIALS_VERSION = '17.2'"));
        eq("ver module Essentials 21.1", "21", ver("module Essentials\n  VERSION = \"21.1\"\n  ERROR_TEXT = ''\nend"));
        eq("ver module Essentials 20.1.dev", "20", ver("module Essentials\n  VERSION = \"20.1.dev\"\nend"));
        eq("ver Essentials::VERSION only", "99", ver("puts Essentials::VERSION"));
        eq("ver none", "0", ver("VERSION = \"3.0\"\nputs 1"));
        eq("ver unrelated VERSION", "0", ver("module Foo\n  VERSION = \"9.9\"\nend"));
        eq("ver newest of two", "21", ver("ESSENTIALS_VERSION = \"16\"\nmodule Essentials\n VERSION = \"21.1\"\nend"));

        // scanning a Scripts.rxdata image
        RuntimeProbe.Essentials e = RuntimeProbe.scanScriptsBytes(scriptsWith("ESSENTIALS_VERSION = \"16.2\""));
        eq("scripts: v16 found", "16.2", e.label);
        e = RuntimeProbe.scanScriptsBytes(scriptsWith("module Essentials\n VERSION = \"21.1\"\nend"));
        eq("scripts: v21 found", "21.1", e.label);
        e = RuntimeProbe.scanScriptsBytes(scriptsWith("# nothing here"));
        eq("scripts: none", "0", String.valueOf(e.major));
        for (int level : new int[] { 0, 1, 9 }) {   // different zlib headers (0x78 01 / 5E / 9C / DA)
            e = RuntimeProbe.scanScriptsBytes(scriptsRxdata(new String[] { "A" }, new String[] { "ESSENTIALS_VERSION = \"18.1\"" }, level));
            eq("scripts: zlib level " + level, "18.1", e.label);
        }
        // junk that looks like zlib headers must not stop the scan
        byte[] real = scriptsWith("ESSENTIALS_VERSION = \"17.2\"");
        ByteArrayOutputStream withJunk = new ByteArrayOutputStream();
        withJunk.write(new byte[] { 0x78, (byte) 0x9c, 1, 2, 3, 0x78, (byte) 0xda, 9, 9, 9, 0x78, 0x01 }, 0, 12);
        withJunk.write(real, 0, real.length);
        e = RuntimeProbe.scanScriptsBytes(withJunk.toByteArray());
        eq("scripts: junk headers ignored", "17.2", e.label);
        e = RuntimeProbe.scanScriptsBytes(new byte[0]);
        eq("scripts: empty", "0", String.valueOf(e.major));
        e = RuntimeProbe.scanScriptsBytes(new byte[] { 4, 8, '[', 6, 0x78, (byte) 0x9c });
        eq("scripts: truncated", "0", String.valueOf(e.major));

        // end to end
        byte[] exe1 = fakeExe(2000, "1.0.3", true, 2), exe2 = fakeExe(2000, "2.0", true, 2);
        RuntimeProbe.Verdict v;

        v = RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("ESSENTIALS_VERSION = \"16.2\""), "Game.exe", exe2));
        eq("e2e Essentials 16, exe 2.0 -> legacy", true, v.legacy);
        v = RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("module Essentials\n VERSION = \"21.1\"\nend"), "Game.exe", exe1));
        eq("e2e Essentials 21 beats exe 1.0.3 -> clean", false, v.legacy);
        v = RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("# custom game"), "Game.exe", exe1));
        eq("e2e no Essentials, exe 1.0.3 -> legacy", true, v.legacy);
        v = RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("# custom game"), "Game.exe", exe2));
        eq("e2e no Essentials, exe 2.0 -> clean", false, v.legacy);
        v = RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("# custom game")));
        eq("e2e nothing at all -> clean", false, v.legacy);
        v = RuntimeProbe.probeRgss1(game("data/SCRIPTS.RXDATA", scriptsWith("ESSENTIALS_VERSION = \"16\""), "game.exe", exe2));
        eq("e2e case-insensitive names", true, v.legacy);
        v = RuntimeProbe.probeRgss1(game("Game.exe", exe1));
        eq("e2e exe only, 1.x -> legacy", true, v.legacy);
        v = RuntimeProbe.probeRgss1(null);
        eq("e2e null dir -> clean", false, v.legacy);
        v = RuntimeProbe.probeRgss1(new File("/does/not/exist"));
        eq("e2e missing dir -> clean", false, v.legacy);
        v = RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", "not a marshal file at all", "Game.exe", exe1));
        eq("e2e corrupt scripts falls back to exe", true, v.legacy);
        System.out.println("   sample reason: " + RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("ESSENTIALS_VERSION = \"16.2\""))).reason);
        System.out.println("   sample reason: " + RuntimeProbe.probeRgss1(game("Data/Scripts.rxdata", scriptsWith("# x"), "Game.exe", exe1)).reason);


        // folder facts (for the log header)
        String f1 = RuntimeProbe.describeFolder(game("Game.exe", exe1, "Game.ini", "x", "Game.rgssad", new byte[3 * 1024 * 1024],
                "Data/Map001.rxdata", "x", "Data/Map003.rxdata", "x", "Data/Scripts.rxdata", "x"));
        System.out.println("   " + f1);
        eq("facts: archive listed", true, f1.contains("archives=Game.rgssad 3MB"));
        eq("facts: Map003 present", true, f1.contains("Map003=yes"));
        eq("facts: Map178 absent", true, f1.contains("Map178=no"));
        eq("facts: Scripts present", true, f1.contains("Scripts.rxdata=yes"));
        eq("facts: map count", true, f1.contains("2 Map*.rxdata"));
        String f2 = RuntimeProbe.describeFolder(game("Game.exe", exe1, "Data/MAP003.RXDATA", "x", "Data/map178.rxdata", "x"));
        System.out.println("   " + f2);
        eq("facts: odd spelling shown", true, f2.contains("Map003=as MAP003.RXDATA") && f2.contains("Map178=as map178.rxdata"));
        eq("facts: no archive", true, f2.contains("archives=none"));
        eq("facts: no Scripts", true, f2.contains("Scripts.rxdata=no"));
        eq("facts: unusual spelling counted", true, f2.contains("2 with unusual spelling"));
        eq("facts: no Data folder", true, RuntimeProbe.describeFolder(game("Game.exe", exe1)).contains("no Data/ folder"));
        eq("facts: null folder", "folder facts: game folder missing", RuntimeProbe.describeFolder(null));
        eq("facts: missing folder", "folder facts: game folder missing", RuntimeProbe.describeFolder(new File("/does/not/exist")));

        System.out.println();
        System.out.println(runs + " checks, " + fails + " failed");
        if (fails != 0) System.exit(1);
    }

    static String ver(String text) {
        RuntimeProbe.Essentials e = RuntimeProbe.findVersion(text.getBytes(StandardCharsets.ISO_8859_1));
        return e == null ? "0" : String.valueOf(e.major);
    }
}
