package com.angus.xliff.convert;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.ResourceBundle;

/** Loads user settings from the filesystem, never from the bundled sample key. */
final class TranslationConfig {
    private final Path path;
    private final String apiKey;
    private final String model;
    private final int batchSize;

    private TranslationConfig(Path path, String apiKey, String model, int batchSize) {
        this.path = path;
        this.apiKey = apiKey;
        this.model = model;
        this.batchSize = batchSize;
    }

    static TranslationConfig load() throws IOException {
        Path workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        try {
            Path codeLocation = Paths.get(TranslationConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path applicationDirectory = Files.isRegularFile(codeLocation) ? codeLocation.getParent() : workingDirectory;
            return load(applicationDirectory, workingDirectory);
        } catch (URISyntaxException | SecurityException e) {
            throw new IOException(message("error_config_location"));
        }
    }

    static TranslationConfig load(Path applicationDirectory, Path workingDirectory) throws IOException {
        Path besideJar = applicationDirectory.resolve("Config.properties").toAbsolutePath().normalize();
        Path inWorkingDirectory = workingDirectory.resolve("Config.properties").toAbsolutePath().normalize();
        Path selected = Files.exists(besideJar) ? besideJar : inWorkingDirectory;
        if (!Files.isRegularFile(selected)) {
            throw new IOException(message("error_config_missing") + besideJar
                    + (besideJar.equals(inWorkingDirectory) ? "" : " ; " + inWorkingDirectory));
        }

        Properties properties = new Properties();
        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(selected, StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') reader.unread(first);
            properties.load(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException(message("error_config_format") + selected);
        }

        String apiKey = properties.getProperty("api_key", properties.getProperty("API_KEY", "")).trim();
        if (apiKey.isEmpty() || "YOUR_API_KEY".equals(apiKey) || hasWhitespaceOrQuotes(apiKey)) {
            throw new IOException(message("error_config_key") + selected);
        }
        String model = properties.getProperty("model_version", "gpt-4o-mini").trim();
        if (model.isEmpty()) model = "gpt-4o-mini";
        int batchSize;
        try {
            batchSize = Integer.parseInt(properties.getProperty("batch_size", "50").trim());
            if (batchSize <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IOException(message("error_config_batch") + selected);
        }
        return new TranslationConfig(selected, apiKey, model, batchSize);
    }

    private static boolean hasWhitespaceOrQuotes(String key) {
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '\'' || ch == '"') return true;
        }
        return false;
    }

    String readPrefix() throws IOException {
        Path prefix = path.getParent().resolve("prefix_promp.txt");
        if (Files.exists(prefix)) {
            return new String(Files.readAllBytes(prefix), StandardCharsets.UTF_8);
        }
        try (InputStream input = TranslationConfig.class.getResourceAsStream("/prefix_promp.txt")) {
            if (input == null) throw new IOException(message("error_prefix_missing") + prefix);
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                StringWriter text = new StringWriter();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) text.write(buffer, 0, count);
                return text.toString();
            }
        }
    }

    private static String message(String key) {
        return ResourceBundle.getBundle("MessagesBundle").getString(key);
    }

    Path getPath() { return path; }
    String getApiKey() { return apiKey; }
    String getModel() { return model; }
    int getBatchSize() { return batchSize; }
}
