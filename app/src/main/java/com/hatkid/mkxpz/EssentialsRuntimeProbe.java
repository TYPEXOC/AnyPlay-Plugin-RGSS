package com.hatkid.mkxpz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final Pattern OLD_VERSION = Pattern.compile(
        "\\bESSENTIALS_VERSION\\s*=\\s*['\\\"]\\s*(\\d{1,2})(?:\\.\\d+)*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIED_VERSION = Pattern.compile(
        "\\bEssentials\\s*::\\s*VERSION\\s*=\\s*['\\\"]\\s*(\\d{1,2})(?:\\.\\d+)*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MODULE_VERSION = Pattern.compile(
        "\\bmodule\\s+Essentials\\b(?s:.{0,4096}?)\\bVERSION\\s*=\\s*['\\\"]\\s*(\\d{1,2})(?:\\.\\d+)*",
        Pattern.CASE_INSENSITIVE);

    static final class Result
    {
        final int rubyRuntime;
        final String reason;

        Result(int rubyRuntime, String reason)
        {
            this.rubyRuntime = rubyRuntime;
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

    private EssentialsRuntimeProbe() {}

    static Result detect(File gameFolder)
    {
        File scripts = findScriptsFile(gameFolder);
        if (scripts == null) return new Result(UNKNOWN, "Data/Scripts.rxdata not found");

        long length = scripts.length();
        if (length <= 0 || length > MAX_SCRIPT_FILE_BYTES) {
            return new Result(UNKNOWN, "Data/Scripts.rxdata size is outside the scan limit: " + length);
        }

        byte[] data = readFile(scripts, (int) length);
        if (data == null) return new Result(UNKNOWN, "Data/Scripts.rxdata could not be read");

        long totalInflated = 0;
        int streams = 0;
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
                return new Result(UNKNOWN, "Scripts.rxdata scan stopped at the decompression limit");
            }

            int major = findVersionMajor(section.bytes);
            if (major > 0) {
                int runtime = major <= 18 ? RUBY_187 : RUBY_313;
                return new Result(runtime, "Pokémon Essentials " + major
                    + " detected in Data/Scripts.rxdata; selected Ruby "
                    + (runtime == RUBY_187 ? "1.8.7" : "3.1.3"));
            }

            offset += Math.max(section.consumed, 1);
        }

        return new Result(UNKNOWN, "Scanned " + streams
            + " compressed script sections; no Essentials version was found");
    }

    private static File findScriptsFile(File gameFolder)
    {
        if (gameFolder == null) return null;
        File[] rootEntries = gameFolder.listFiles();
        if (rootEntries == null) return null;

        File dataFolder = null;
        for (File entry : rootEntries) {
            if (entry.isDirectory() && "data".equals(entry.getName().toLowerCase(Locale.ROOT))) {
                dataFolder = entry;
                break;
            }
        }
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
