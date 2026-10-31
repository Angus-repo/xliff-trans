package com.angus.xliff.convert;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TranslationConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void prefersConfigBesideJarOverWorkingDirectory() throws Exception {
        Path app = temporary.newFolder("application").toPath();
        Path cwd = temporary.newFolder("working").toPath();
        write(app, "api_key=test-app\nmodel_version=my-model\nbatch_size=7\n");
        write(cwd, "api_key=test-working\n");
        TranslationConfig config = TranslationConfig.load(app, cwd);
        assertEquals("test-app", config.getApiKey());
        assertEquals("my-model", config.getModel());
        assertEquals(7, config.getBatchSize());
        assertEquals(app.resolve("Config.properties"), config.getPath());
    }

    @Test public void fallsBackToWorkingDirectoryWhenNoConfigBesideJar() throws Exception {
        Path app = temporary.newFolder().toPath();
        Path cwd = temporary.newFolder().toPath();
        write(cwd, "api_key=test-working\n");
        TranslationConfig config = TranslationConfig.load(app, cwd);
        assertEquals("test-working", config.getApiKey());
        assertEquals("gpt-4o-mini", config.getModel());
        assertEquals(50, config.getBatchSize());
    }

    @Test public void neverUsesBundledPlaceholderWhenExternalFileMissing() throws Exception {
        Path app = temporary.newFolder().toPath();
        Path cwd = temporary.newFolder().toPath();
        IOException error = assertThrows(IOException.class, () -> TranslationConfig.load(app, cwd));
        assertTrue(error.getMessage().contains(app.resolve("Config.properties").toString()));
        assertTrue(error.getMessage().contains(cwd.resolve("Config.properties").toString()));
    }

    @Test public void acceptsUtf8BomAndSurroundingWhitespace() throws Exception {
        Path app = temporary.newFolder().toPath();
        write(app, "\uFEFFapi_key= test-new-key  \nmodel_version= test-model  \nbatch_size= 3 \n");
        TranslationConfig config = TranslationConfig.load(app, app);
        assertEquals("test-new-key", config.getApiKey());
        assertEquals("test-model", config.getModel());
        assertEquals(3, config.getBatchSize());
    }

    @Test public void acceptsLegacyUppercaseKeyButPrefersLowercase() throws Exception {
        Path app = temporary.newFolder().toPath();
        write(app, "API_KEY=test-legacy\n");
        assertEquals("test-legacy", TranslationConfig.load(app, app).getApiKey());
        write(app, "API_KEY=test-legacy\napi_key=test-preferred\n");
        assertEquals("test-preferred", TranslationConfig.load(app, app).getApiKey());
    }

    @Test public void doesNotFallBackAfterFindingAnInvalidExternalConfig() throws Exception {
        Path app = temporary.newFolder().toPath();
        Path cwd = temporary.newFolder().toPath();
        write(app, "api_key=YOUR_API_KEY\n");
        write(cwd, "api_key=test-other\n");
        IOException error = assertThrows(IOException.class, () -> TranslationConfig.load(app, cwd));
        assertTrue(error.getMessage().contains(app.resolve("Config.properties").toString()));
    }

    @Test public void rejectsMissingPlaceholderAndUnsafeKeysWithoutEchoingThem() throws Exception {
        Path app = temporary.newFolder().toPath();
        for (String key : new String[] {"", "YOUR_API_KEY", "test-secret with-space", "\"test-secret\"", "test-secret\\nheader"}) {
            write(app, "api_key=" + key + "\n");
            IOException error = assertThrows(IOException.class, () -> TranslationConfig.load(app, app));
            assertFalse(error.getMessage().contains("test-secret"));
        }
    }

    @Test public void rejectsInvalidBatchSizesWithoutEchoingTheKey() throws Exception {
        Path app = temporary.newFolder().toPath();
        for (String size : new String[] {"0", "-1", "abc", "2147483648"}) {
            write(app, "api_key=test-secret\nbatch_size=" + size + "\n");
            IOException error = assertThrows(IOException.class, () -> TranslationConfig.load(app, app));
            assertTrue(error.getMessage().contains("batch_size"));
            assertFalse(error.getMessage().contains("test-secret"));
        }
    }

    @Test public void reloadsUpdatedKeyWithoutRestarting() throws Exception {
        Path app = temporary.newFolder().toPath();
        write(app, "api_key=test-old\n");
        assertEquals("test-old", TranslationConfig.load(app, app).getApiKey());
        write(app, "api_key=test-new\n");
        assertEquals("test-new", TranslationConfig.load(app, app).getApiKey());
    }

    @Test public void stepTwoReloadsAndDoesNotReuseSettingsAfterAFileIsRemoved() throws Exception {
        Path app = temporary.newFolder().toPath();
        String original = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", app.toString());
            Path input = app.resolve("input.xliff");
            Files.write(Paths.get(input + Constans.SOURCE_FILE_NAME_POSTFIX), "<source id=\"1\">text</source>\n".getBytes(StandardCharsets.UTF_8));
            write(app, "api_key=test-valid\n");
            assertEquals(1, ChatGPTClient.getTotalLines(input.toString()));
            assertEquals(app.resolve("Config.properties").toString(), ChatGPTClient.getConfigurationPath());
            Files.delete(app.resolve("Config.properties"));
            assertThrows(IOException.class, () -> ChatGPTClient.getTotalLines(input.toString()));
            assertEquals("", ChatGPTClient.getConfigurationPath());
            assertThrows(IOException.class, () -> ChatGPTClient.processNextBatch(input.toString()));
        } finally {
            System.setProperty("user.dir", original);
        }
    }

    @Test public void readsPromptBesideTheSelectedConfig() throws Exception {
        Path app = temporary.newFolder().toPath();
        write(app, "api_key=test-key\n");
        Files.write(app.resolve("prefix_promp.txt"), "繁體中文提示詞\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("繁體中文提示詞\n", TranslationConfig.load(app, app).readPrefix());
    }

    @Test public void fallsBackToBundledPromptWhenExternalPromptIsMissing() throws Exception {
        Path app = temporary.newFolder().toPath();
        write(app, "api_key=test-key\n");
        assertFalse(TranslationConfig.load(app, app).readPrefix().isEmpty());
    }

    @Test public void packagedJarUsesItsOwnDirectoryEvenWhenLaunchedElsewhere() throws Exception {
        Path app = temporary.newFolder("程式 with spaces").toPath();
        Path cwd = temporary.newFolder("other working directory").toPath();
        write(app, "api_key=test-beside-jar\n");
        write(cwd, "api_key=test-wrong-directory\n");
        Files.write(app.resolve("prefix_promp.txt"), "external-prompt".getBytes(StandardCharsets.UTF_8));
        Path jar = app.resolve("test app.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, PackagedProbe.class.getName());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            copyResource(out, TranslationConfig.class.getName().replace('.', '/') + ".class");
            copyResource(out, PackagedProbe.class.getName().replace('.', '/') + ".class");
            copyResource(out, "MessagesBundle.properties");
            copyResource(out, "Config.properties"); // Deliberately contains YOUR_API_KEY.
        }
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-jar", jar.toString()).directory(cwd.toFile())
                .redirectErrorStream(true).start();
        String output;
        try (java.util.Scanner scanner = new java.util.Scanner(process.getInputStream(), "UTF-8").useDelimiter("\\A")) {
            output = scanner.hasNext() ? scanner.next() : "";
        }
        assertEquals(output, 0, process.waitFor());
        assertTrue(output.contains("external configuration loaded"));
    }

    public static class PackagedProbe {
        public static void main(String[] args) throws Exception {
            TranslationConfig config = TranslationConfig.load();
            if (!"test-beside-jar".equals(config.getApiKey())) throw new AssertionError("Wrong configuration selected");
            if (!"external-prompt".equals(config.readPrefix())) throw new AssertionError("Wrong prompt selected");
            System.out.println("external configuration loaded");
        }
    }

    private static void copyResource(JarOutputStream out, String name) throws IOException {
        out.putNextEntry(new JarEntry(name));
        try (InputStream input = TranslationConfigTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, input);
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        out.closeEntry();
    }

    private static void write(Path directory, String content) throws IOException {
        Files.write(directory.resolve("Config.properties"), content.getBytes(StandardCharsets.UTF_8));
    }
}
