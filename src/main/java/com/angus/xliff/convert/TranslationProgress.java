package com.angus.xliff.convert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Completed IDs come from durable output, including files written by older versions. */
final class TranslationProgress {
    private static final Pattern RECORD = Pattern.compile("<source\\b([^>]*)>(.*?)</source\\s*>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("(?:^|\\s)id\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL);
    private static final Pattern OPEN = Pattern.compile("<source\\b");
    private static final Pattern CLOSE = Pattern.compile("</source\\s*>");
    private final Path input;
    private final Path output;
    private final LinkedHashMap<String, String> sources;
    private final Set<String> completed;
    private final String sourceDigest;
    private byte[] savedOutput;

    private TranslationProgress(Path input, Path output, LinkedHashMap<String, String> sources,
                                Set<String> completed, byte[] savedOutput, String sourceDigest) {
        this.input = input;
        this.output = output;
        this.sources = sources;
        this.completed = completed;
        this.savedOutput = savedOutput;
        this.sourceDigest = sourceDigest;
    }

    static TranslationProgress open(String filePath) throws IOException {
        Path input = Paths.get(filePath).toAbsolutePath().normalize();
        byte[] sourceBytes = Files.readAllBytes(Paths.get(filePath + Constans.SOURCE_FILE_NAME_POSTFIX));
        LinkedHashMap<String, String> sources = parseRecords(new String(sourceBytes, StandardCharsets.UTF_8));
        Path output = Paths.get(filePath + Constans.TARGET_FILE_NAME_POSTFIX).toAbsolutePath().normalize();
        byte[] previous = Files.exists(output) ? Files.readAllBytes(output) : new byte[0];
        LinkedHashMap<String, String> translated = parseRecords(new String(previous, StandardCharsets.UTF_8));
        if (!sources.keySet().containsAll(translated.keySet())) throw error("error_progress_mismatch");

        // Persist the input fingerprint before any request. On restart the output itself
        // is the commit record, avoiding a second progress write after a successful batch.
        Path state = Paths.get(filePath + "_translation_state.properties");
        String digest = sha256(sourceBytes);
        if (Files.exists(state)) {
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(state, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            if (!digest.equals(properties.getProperty("source_sha256"))) throw error("error_progress_changed");
        } else {
            // Legacy output has no fingerprint: validate its complete IDs before adopting it.
            atomicWrite(state, ("version=1\nsource_sha256=" + digest + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return new TranslationProgress(input, output, sources, new LinkedHashSet<>(translated.keySet()), previous, digest);
    }

    int total() { return sources.size(); }
    int completedCount() { return completed.size(); }
    boolean isFor(String filePath) { return input.equals(Paths.get(filePath).toAbsolutePath().normalize()); }

    LinkedHashMap<String, String> nextBatch(int size) {
        List<String> pending = new ArrayList<>();
        for (String id : sources.keySet()) if (!completed.contains(id)) pending.add(id);
        LinkedHashMap<String, String> batch = new LinkedHashMap<>();
        // Preserve the existing last-to-first batch order when resuming old output.
        for (int i = Math.max(pending.size() - size, 0); i < pending.size(); i++) {
            String id = pending.get(i);
            batch.put(id, sources.get(id));
        }
        return batch;
    }

    void commit(Map<String, String> batch, String response) throws IOException {
        LinkedHashMap<String, String> translated = parseRecords(response);
        if (!translated.keySet().equals(batch.keySet()) || !sources.keySet().containsAll(batch.keySet())) {
            throw error("error_batch_incomplete");
        }
        for (String id : batch.keySet()) if (completed.contains(id)) throw error("error_progress_mismatch");
        if (!sourceDigest.equals(sha256(Files.readAllBytes(
                Paths.get(input + Constans.SOURCE_FILE_NAME_POSTFIX))))) throw error("error_progress_changed");
        byte[] current = Files.exists(output) ? Files.readAllBytes(output) : new byte[0];
        if (!Arrays.equals(savedOutput, current)) throw error("error_output_changed");
        StringBuilder appended = new StringBuilder(new String(savedOutput, StandardCharsets.UTF_8));
        if (appended.length() > 0 && appended.charAt(appended.length() - 1) != '\n') appended.append('\n');
        for (String record : translated.values()) {
            // The merge step consumes one record per line; preserve text line breaks explicitly.
            appended.append(record.replace("\r\n", "\n").replace("\r", "\n")
                    .replace("\n", "${NEW_LINE}")).append('\n');
        }
        byte[] next = appended.toString().getBytes(StandardCharsets.UTF_8);
        atomicWrite(output, next);
        savedOutput = next;
        completed.addAll(batch.keySet());
    }

    static LinkedHashMap<String, String> parseRecords(String text) throws IOException {
        LinkedHashMap<String, String> records = new LinkedHashMap<>();
        Matcher matcher = RECORD.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            Matcher id = ID.matcher(matcher.group(1));
            if (!id.find() || id.group(2).isEmpty() || matcher.group(2).trim().isEmpty()) {
                throw error("error_batch_incomplete");
            }
            String value = id.group(2);
            if (id.find() || records.put(value, matcher.group()) != null) throw error("error_progress_duplicate");
        }
        if (countMatches(OPEN, text) != count || countMatches(CLOSE, text) != count
                || (count == 0 && !text.trim().isEmpty())) throw error("error_batch_incomplete");
        return records;
    }

    static String content(String record) {
        Matcher matcher = RECORD.matcher(record);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid translation record");
        return matcher.group(2);
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static String sha256(byte[] content) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void atomicWrite(Path path, byte[] bytes) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(absolute.getParent(), "xliff-progress-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw error("error_atomic_save");
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static IOException error(String key) {
        return new IOException(ResourceBundle.getBundle("MessagesBundle").getString(key));
    }
}
