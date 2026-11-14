package com.hatkid.mkxpz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Detects Pokémon Essentials' Ruby era from loose XP Scripts.rxdata data. */
final class EssentialsRuntimeProbe
{
    static final int UNKNOWN = 0;
    static final int RUBY_187 = 187;
    static final int RUBY_313 = 313;

    private static final int MAX_SCRIPT_FILE_BYTES = 40 * 1024 * 1024;
    private static final int MAX_INFLATED_SECTION_BYTES = 8 * 1024 * 1024;
    private static final long MAX_TOTAL_INFLATED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_LOOSE_SCRIPT_FILES = 4096;
    private static final Pattern OLD_VERSION = Pattern.compile(
        "\\bESSENTIALS_VERSION\\s*=\\s*['\\\"]\\s*(\\d{1,2})(?:\\.\\d+)*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIED_VERSION = Pattern.compile(
        "\\bEssentials\\s*::\\s*VERSION\\s*=\\s*['\\\"]\\s*(\\d{1,2})(?:\\.\\d+)*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MODULE_VERSION = Pattern.compile(
        "\\bmodule\\s+Essentials\\b(?s:.{0,4096}?)\\bVERSION\\s*=\\s*['\\\"]\\s*(\\d{1,2})(?:\\.\\d+)*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern BATTLE_CLASS = Pattern.compile("\\b(?:class|module)\\s+Battle\\b");
    private static final Pattern POKEMON_CLASS = Pattern.compile("\\b(?:class|module)\\s+Pokemon\\b");
    private static final Pattern GAME_DATA_MODULE = Pattern.compile("\\b(?:class|module)\\s+GameData\\b");

    static final class Result
    {
        final int rubyRuntime;
        final boolean essentialsDetected;
        final String reason;

        Result(int rubyRuntime, String reason)
        {
            this(rubyRuntime, rubyRuntime != UNKNOWN, reason);
        }

        Result(int rubyRuntime, boolean essentialsDetected, String reason)
        {
            this.rubyRuntime = rubyRuntime;
            this.essentialsDetected = essentialsDetected;
            this.reason = reason;
        }
    }

    private static final class InflatedSection
    {
        final byte[] bytes;
        final int consumed;

        InflatedSection(byte[] bytes, int consumed)
        {
            this.bytes = bytes;
            this.consumed = consumed;
        }
    }

    private static final class ScanEvidence
    {
        int versionMajor;
        boolean oldBattle;
        boolean oldPokemon;
        boolean bag;
        boolean battle;
        boolean pokemon;
        boolean gameData;

        boolean hasEssentialsMarkers()
        {
            return (oldBattle && bag && (oldPokemon || pokemon))
                || (battle && pokemon && bag && gameData);
        }
    }

    private EssentialsRuntimeProbe() {}

    static Result detect(File gameFolder)
    {
        File scripts = findScriptsFile(gameFolder);
        ScanEvidence evidence = new ScanEvidence();
        long totalInflated = 0;
        int streams = 0;
        String archiveNote;
        if (scripts == null) {
            archiveNote = "Data/Scripts.rxdata not found";
        } else {
            long length = scripts.length();
            if (length <= 0 || length > MAX_SCRIPT_FILE_BYTES) {
                archiveNote = "Data/Scripts.rxdata size is outside the scan limit: " + length;
            } else {
                byte[] data = readFile(scripts, (int) length);
                if (data == null) {
                    archiveNote = "Data/Scripts.rxdata could not be read";
                } else {
                    archiveNote = "Scanned Data/Scripts.rxdata";
                    for (int offset = 0; offset + 2 < data.length;) {
                        if (!isZlibHeader(data, offset)) {
                            offset++;
                            continue;
                        }

                        InflatedSection section = inflateAt(data, offset);
                        if (section == null) {
                            offset++;
                            continue;
                        }

                        streams++;
                        totalInflated += section.bytes.length;
                        if (totalInflated > MAX_TOTAL_INFLATED_BYTES) {
                            archiveNote = "Scripts.rxdata scan stopped at the decompression limit";
                            break;
                        }

                        scanText(section.bytes, evidence);
                        offset += Math.max(section.consumed, 1);
                    }
                }
            }
        }

        int looseScripts = scanLooseScripts(findScriptsDirectory(gameFolder), evidence);
        if (evidence.versionMajor > 0) {
            int runtime = evidence.versionMajor <= 18 ? RUBY_187 : RUBY_313;
            return new Result(runtime, true, "Pokémon Essentials " + evidence.versionMajor
                + " detected in game scripts; selected Ruby "
                + (runtime == RUBY_187 ? "1.8.7" : "3.1.3"));
        }
        if (evidence.hasEssentialsMarkers()) {
            return new Result(UNKNOWN, true, "Pokémon Essentials API markers detected in game scripts"
                + " (compressed sections=" + streams + ", loose scripts=" + looseScripts + ")");
        }
        return new Result(UNKNOWN, false, archiveNote + "; scanned compressed sections=" + streams
            + ", loose scripts=" + looseScripts + "; no Essentials markers found");
    }

    private static int scanLooseScripts(File scriptsDirectory, ScanEvidence evidence)
    {
        if (scriptsDirectory == null || !scriptsDirectory.isDirectory()) return 0;
        List<File> pending = new ArrayList<>();
        pending.add(scriptsDirectory);
        int filesScanned = 0;
        int directoriesScanned = 0;
        long totalBytes = 0;
        while (!pending.isEmpty() && filesScanned < MAX_LOOSE_SCRIPT_FILES
            && directoriesScanned < MAX_LOOSE_SCRIPT_FILES && totalBytes < MAX_TOTAL_INFLATED_BYTES) {
            File directory = pending.remove(pending.size() - 1);
            directoriesScanned++;
            File[] entries = directory.listFiles();
            if (entries == null) continue;
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    if (pending.size() + directoriesScanned < MAX_LOOSE_SCRIPT_FILES) pending.add(entry);
                    continue;
                }
                if (!entry.isFile() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".rb")) continue;
                long length = entry.length();
                if (length <= 0 || length > MAX_SCRIPT_FILE_BYTES || totalBytes + length > MAX_TOTAL_INFLATED_BYTES) continue;
                byte[] data = readFile(entry, (int) length);
                filesScanned++;
                if (data != null) {
                    totalBytes += data.length;
                    scanText(data, evidence);
                }
            }
        }
        return filesScanned;
    }

    private static void scanText(byte[] source, ScanEvidence evidence)
    {
        String text = new String(source, StandardCharsets.ISO_8859_1);
        if (evidence.versionMajor == 0) evidence.versionMajor = findVersionMajor(source);
        evidence.oldBattle |= text.contains("PokeBattle_Battle");
        evidence.oldPokemon |= text.contains("PokeBattle_Pokemon");
        evidence.bag |= text.contains("PokemonBag");
        evidence.battle |= BATTLE_CLASS.matcher(text).find();
        evidence.pokemon |= POKEMON_CLASS.matcher(text).find();
        evidence.gameData |= GAME_DATA_MODULE.matcher(text).find();
    }

    private static File findScriptsFile(File gameFolder)
    {
        File dataFolder = findDataFolder(gameFolder);
        if (dataFolder == null) return null;

        File[] dataEntries = dataFolder.listFiles();
        if (dataEntries == null) return null;
        for (File entry : dataEntries) {
            if (entry.isFile() && "scripts.rxdata".equals(entry.getName().toLowerCase(Locale.ROOT))) {
                return entry;
            }
        }
        return null;
    }

    private static File findScriptsDirectory(File gameFolder)
    {
        File dataFolder = findDataFolder(gameFolder);
        if (dataFolder == null) return null;
        File[] entries = dataFolder.listFiles();
        if (entries == null) return null;
        for (File entry : entries) {
            if (entry.isDirectory() && "scripts".equals(entry.getName().toLowerCase(Locale.ROOT))) return entry;
        }
        return null;
    }

    private static File findDataFolder(File gameFolder)
    {
        if (gameFolder == null) return null;
        File[] rootEntries = gameFolder.listFiles();
        if (rootEntries == null) return null;
        for (File entry : rootEntries) {
            if (entry.isDirectory() && "data".equals(entry.getName().toLowerCase(Locale.ROOT))) return entry;
        }
        return null;
    }

    private static byte[] readFile(File file, int expectedLength)
    {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_SCRIPT_FILE_BYTES) return null;
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException error) {
            return null;
        }
    }

    private static boolean isZlibHeader(byte[] data, int offset)
    {
        int cmf = data[offset] & 0xff;
        int flg = data[offset + 1] & 0xff;
        return (cmf & 0x0f) == 8 && (cmf >>> 4) <= 7
            && (((cmf << 8) | flg) % 31) == 0;
    }

    private static InflatedSection inflateAt(byte[] data, int offset)
    {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data, offset, data.length - offset);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count > 0) {
                    if (output.size() + count > MAX_INFLATED_SECTION_BYTES) return null;
                    output.write(buffer, 0, count);
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    return null;
                } else {
                    return null;
                }
            }

            long consumed = inflater.getBytesRead();
            if (consumed <= 0 || consumed > Integer.MAX_VALUE) return null;
            return new InflatedSection(output.toByteArray(), (int) consumed);
        } catch (DataFormatException error) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static int findVersionMajor(byte[] source)
    {
        String text = new String(source, StandardCharsets.ISO_8859_1);
        int major = findMajor(OLD_VERSION, text);
        if (major == 0) major = findMajor(QUALIFIED_VERSION, text);
        if (major == 0) major = findMajor(MODULE_VERSION, text);
        return major;
    }

    private static int findMajor(Pattern pattern, String text)
    {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
