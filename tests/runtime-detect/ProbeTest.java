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


    /** RGSS archive v1 writer (same algorithm as the engine's reader in src/crypto/rgssad.cpp). */
    static byte[] rgssad(String[] names, int[] sizes) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] head = { 'R', 'G', 'S', 'S', 'A', 'D', 0, 1 };
        o.write(head, 0, 8);
        int magic = 0xDEADCAFE;
        for (int i = 0; i < names.length; i++) {
            byte[] n = names[i].replace('/', '\\').getBytes(StandardCharsets.ISO_8859_1);
            le32(o, n.length ^ magic); magic = magic * 7 + 3;
            for (byte b : n) { o.write((b ^ (magic & 0xff)) & 0xff); magic = magic * 7 + 3; }
            le32(o, sizes[i] ^ magic); magic = magic * 7 + 3;
            byte[] data = new byte[sizes[i]];
            new Random(i).nextBytes(data);
            o.write(data, 0, data.length);
        }
        return o.toByteArray();
    }

    static void le32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff); o.write((v >> 8) & 0xff); o.write((v >> 16) & 0xff); o.write((v >> 24) & 0xff);
    }

    static File oneFile(String name, byte[] data) throws Exception {
        return new File(game(name, data), name);
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


        // RGSS archive name lister
        byte[] arc = rgssad(new String[] { "Data/Scripts.rxdata", "Data/Map001.rxdata", "Data/Map003.rxdata",
                "Data/Map070.rxdata", "Graphics/Icons/a.png", "Data/MapInfos.rxdata" },
                new int[] { 50, 20, 33, 7, 1000, 5 });
        String a1 = RuntimeProbe.describeArchive(oneFile("Game.rgssad", arc));
        System.out.println("   " + a1);
        eq("archive: entry count", true, a1.contains("holds 6 files"));
        eq("archive: Data count", true, a1.contains("5 in Data/ (3 Map*.rxdata)"));
        eq("archive: Scripts yes", true, a1.contains("Scripts.rxdata=yes"));
        eq("archive: Map003 yes", true, a1.contains("Map003=yes"));
        eq("archive: Map178 no", true, a1.contains("Map178=no"));
        eq("archive: lowest maps", true, a1.contains("lowest map numbers 1,3,70"));

        String a2 = RuntimeProbe.describeArchive(oneFile("Game.rgssad", rgssad(
                new String[] { "Data/Map001.rxdata", "Data/Map178.rxdata", "DATA/MAP002.RXDATA" }, new int[] { 10, 10, 10 })));
        eq("archive: Map003 absent", true, a2.contains("Map003=no"));
        eq("archive: Map178 present", true, a2.contains("Map178=yes"));
        eq("archive: names are case-insensitive", true, a2.contains("3 in Data/ (3 Map*.rxdata)") && a2.contains("1,2,178"));

        byte[] cut = new byte[arc.length - 600];
        System.arraycopy(arc, 0, cut, 0, cut.length);
        String a3 = RuntimeProbe.describeArchive(oneFile("Game.rgssad", cut));
        System.out.println("   " + a3);
        eq("archive: truncated is reported", true, a3.contains("runs past the end"));

        eq("archive: not RGSS", "Game.rgssad: not an RGSS archive", RuntimeProbe.describeArchive(oneFile("Game.rgssad", new byte[100])));
        byte[] v3 = { 'R', 'G', 'S', 'S', 'A', 'D', 0, 3, 1, 2, 3, 4 };
        eq("archive: v3 reported", "Game.rgss3a: format v3 (names not read)", RuntimeProbe.describeArchive(oneFile("Game.rgss3a", v3)));
        eq("archive: empty file", "Game.rgssad: not an RGSS archive", RuntimeProbe.describeArchive(oneFile("Game.rgssad", new byte[0])));
        eq("archive: header only", true, RuntimeProbe.describeArchive(oneFile("Game.rgssad", new byte[] { 'R', 'G', 'S', 'S', 'A', 'D', 0, 1 })).contains("holds 0 files"));

        String f3 = RuntimeProbe.describeFolder(game("Game.exe", exe1, "Game.rgssad", arc, ".kirin-update.lock", "x", ".nomedia", "x",
                "Data/Misc.dat", "x"));
        System.out.println("   " + f3);
        eq("facts: archive described in folder line", true, f3.contains("Game.rgssad holds 6 files") && f3.contains("Map003=yes"));
        eq("facts: kirin leftovers flagged", true, f3.contains(".kirin* entries=1"));
        eq("facts: small Data/ lists names", true, f3.contains("Data/ has 1 files (Misc.dat)"));


        // stored spelling + mkxp.json settings
        String a4 = RuntimeProbe.describeArchive(oneFile("Game.rgssad", rgssad(
                new String[] { "Data/Map001.rxdata", "Data/map003.rxdata", "DATA/MAP178.RXDATA" }, new int[] { 4, 4, 4 })));
        System.out.println("   " + a4);
        eq("archive: odd spelling of Map003 shown", true, a4.contains("Map003=yes (stored as Data/map003.rxdata)"));
        eq("archive: odd spelling of Map178 shown", true, a4.contains("Map178=yes (stored as DATA/MAP178.RXDATA)"));
        eq("archive: usual spelling not repeated", true, a1.contains("Map003=yes,") && !a1.contains("stored as"));
        String f4 = RuntimeProbe.describeFolder(game("mkxp.json", "{ // c\n \"pathCache\": false,\n \"rgssVersion\" : 1 }", "Game.exe", exe1));
        System.out.println("   " + f4);
        eq("facts: mkxp.json pathCache=false", true, f4.contains("mkxp.json: pathCache=false rgssVersion=1"));
        eq("facts: mkxp.json without keys", true, RuntimeProbe.describeFolder(game("mkxp.json", "{ \"fps\": 40 }")).contains("mkxp.json: no pathCache/rgssVersion keys"));
        eq("facts: no mkxp.json adds nothing", false, RuntimeProbe.describeFolder(game("Game.exe", exe1)).contains("mkxp.json"));

        System.out.println();
        System.out.println(runs + " checks, " + fails + " failed");
        if (fails != 0) System.exit(1);
    }

    static String ver(String text) {
        RuntimeProbe.Essentials e = RuntimeProbe.findVersion(text.getBytes(StandardCharsets.ISO_8859_1));
        return e == null ? "0" : String.valueOf(e.major);
    }
}
