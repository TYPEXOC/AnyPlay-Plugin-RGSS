import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Host test for RuntimeDetector (package com.hatkid.mkxpz). Run with run.sh. */
public class SelfTest {
    static int fails = 0, runs = 0;
    static Class<?> C; static Class<?> D;

    static File mk(String... spec) throws Exception {   // "path" or "path=content"
        File root = Files.createTempDirectory("rtd").toFile();
        for (String s : spec) {
            int eq = s.indexOf('=');
            String path = eq < 0 ? s : s.substring(0, eq);
            byte[] data = eq < 0 ? new byte[0] : s.substring(eq + 1).replace("\\n", "\n").getBytes(StandardCharsets.ISO_8859_1);
            File f = new File(root, path);
            f.getParentFile().mkdirs();
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
        }
        return root;
    }

    static int rgss(File dir) throws Exception {
        Method m = C.getDeclaredMethod("detect", File.class); m.setAccessible(true);
        Object d = m.invoke(null, dir);
        java.lang.reflect.Field f = D.getDeclaredField("rgss"); f.setAccessible(true);
        return f.getInt(d);
    }
    static String why(File dir) throws Exception {
        Method m = C.getDeclaredMethod("detect", File.class); m.setAccessible(true);
        Object d = m.invoke(null, dir);
        java.lang.reflect.Field f = D.getDeclaredField("reason"); f.setAccessible(true);
        return (String) f.get(d);
    }
    static void eq(String name, Object want, Object got) {
        runs++;
        if (!String.valueOf(want).equals(String.valueOf(got))) { fails++; System.out.println("FAIL " + name + ": want " + want + " got " + got); }
        else System.out.println("ok   " + name);
    }

    public static void main(String[] a) throws Exception {
        C = Class.forName("com.hatkid.mkxpz.RuntimeDetector");
        D = Class.forName("com.hatkid.mkxpz.RuntimeDetector$Detection");

        eq("XP Library RGSS102E", 1, rgss(mk("Game.ini=[Game]\\nLibrary=RGSS102E.dll\\nTitle=X\\n")));
        eq("XP Scripts rxdata", 1, rgss(mk("Game.ini=[Game]\\nScripts=Data\\\\Scripts.rxdata\\n")));
        eq("VX Scripts rvdata", 2, rgss(mk("Game.ini=[Game]\\nScripts=Data\\\\Scripts.rvdata\\nLibrary=RGSS202E.dll\\n")));
        eq("Ace Scripts rvdata2", 3, rgss(mk("Game.ini=[Game]\\nScripts=Data\\\\Scripts.rvdata2\\nLibrary=RGSS301.dll\\n")));
        eq("Library only, Ace", 3, rgss(mk("Game.ini=[Game]\\nLibrary=System\\\\RGSS301.dll\\n")));
        eq("Library only, XP in System dir", 1, rgss(mk("Game.ini=[Game]\\nLibrary=System\\\\RGSS104J.dll\\n")));
        eq("lowercase file + key names", 1, rgss(mk("game.INI=[game]\\nlibrary=rgss102e.dll\\n")));
        eq("CRLF line endings", 1, rgss(mk("Game.ini=[Game]\r\\nLibrary=RGSS102E.dll\r\\n")));
        eq("no ini, Data/Scripts.rxdata", 1, rgss(mk("data/scripts.RXDATA")));
        eq("no ini, Data/Scripts.rvdata2", 3, rgss(mk("Data/Scripts.rvdata2")));
        eq("only archive rgss3a", 3, rgss(mk("Game.rgss3a")));
        eq("only archive rgssad", 1, rgss(mk("Game.rgssad")));
        eq("nothing at all", 0, rgss(mk("readme.txt")));
        eq("empty dir", 0, rgss(mk()));
        eq("missing dir", 0, rgss(new File("/nonexistent/xyz")));
        eq("null dir", 0, rgss(null));
        eq("mkxp.json wins over ini", 3, rgss(mk("mkxp.json={ \"rgssVersion\": 3 }", "Game.ini=[Game]\\nLibrary=RGSS102E.dll\\nScripts=Data\\\\Scripts.rxdata\\n")));
        eq("mkxp.json commented out", 1, rgss(mk("mkxp.json={\\n // \"rgssVersion\": 3\\n \"rgssVersion\": 0\\n}", "Game.ini=[Game]\\nLibrary=RGSS102E.dll\\n")));
        eq("Scripts beats Library (engine rule)", 1, rgss(mk("Game.ini=[Game]\\nScripts=Data\\\\Scripts.rxdata\\nLibrary=RGSS301.dll\\n")));
        eq("execName from mkxp.json", 3, rgss(mk("mkxp.json={ \"execName\": \"Pokemon\" }", "Pokemon.ini=[Game]\\nLibrary=RGSS301.dll\\n", "Game.ini=[Game]\\nLibrary=RGSS102E.dll\\n")));
        eq("ini with UTF-8 BOM", 1, rgss(mk("Game.ini=ï»¿[Game]\\nLibrary=RGSS102E.dll\\n")));
        eq("Library without digit after RGSS", 0, rgss(mk("Game.ini=[Game]\\nLibrary=RGSSX.dll\\n")));
        eq("Library RGSS9 invalid", 0, rgss(mk("Game.ini=[Game]\\nLibrary=RGSS900.dll\\n")));
        // UTF-16LE ini
        File f = mk(); try (FileOutputStream o = new FileOutputStream(new File(f, "Game.ini"))) {
            o.write(new byte[]{(byte)0xFF,(byte)0xFE}); o.write("[Game]\r\nLibrary=RGSS301.dll\r\n".getBytes(StandardCharsets.UTF_16LE)); }
        eq("ini UTF-16LE", 3, rgss(f));
        // disagreement is reported
        String r = why(mk("Game.ini=[Game]\\nScripts=Data\\\\Scripts.rxdata\\nLibrary=RGSS301.dll\\n"));
        eq("conflict text present", true, r.contains("disagree"));
        // reason never has non-ASCII / newlines
        String r2 = why(mk("Game.ini=[Game]\\nScripts=Data\\\\Scréipts.rxdata\\n"));
        eq("reason ascii", true, r2.chars().allMatch(c -> c >= 0x20 && c < 0x7f));

        // static helpers
        Method rs = C.getDeclaredMethod("rgssFromScriptsName", String.class); rs.setAccessible(true);
        eq("rvdata2 not mistaken for rvdata", 3, rs.invoke(null, "Data\\Scripts.rvdata2"));
        // override file
        Method ro = C.getDeclaredMethod("readOverride", File.class); ro.setAccessible(true);
        eq("override r3", "r3", ro.invoke(null, mk("anyplay_runtime.txt=r3\\n")));
        eq("override with comments/case", "r18", ro.invoke(null, mk("ANYPLAY_RUNTIME.TXT=# note\\n\\n  R18  \\n")));
        eq("override auto", "auto", ro.invoke(null, mk("anyplay_runtime.txt=auto")));
        eq("override missing", "null", ro.invoke(null, mk("x.txt")));
        eq("override empty file", "null", ro.invoke(null, mk("anyplay_runtime.txt")));

        System.out.println(fails == 0 ? "ALL " + runs + " TESTS PASSED" : fails + " OF " + runs + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
