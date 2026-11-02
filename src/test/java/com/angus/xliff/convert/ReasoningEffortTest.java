package com.angus.xliff.convert;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReasoningEffortTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void oldConfigWithoutEffortExplicitlySendsMediumForLuna() throws Exception {
        TranslationConfig config = config("gpt-6-luna", "");
        JsonNode request = request(config);
        assertEquals("medium", request.path("reasoning_effort").asText());
        assertEquals("gpt-6-luna", request.path("model").asText());
        assertEquals("user", request.path("messages").path(0).path("role").asText());
        assertEquals("translate these records", request.path("messages").path(0).path("content").asText());
        assertFalse(request.toString().contains("test-secret"));
    }

    @Test public void sendsEachConfiguredLunaEffortIncludingExplicitNone() throws Exception {
        for (String effort : new String[] {"none", "low", "medium", "high", "xhigh", "max"}) {
            JsonNode request = request(config("gpt-6-luna", "reasoning_effort=" + effort + "\n"));
            assertEquals(effort, request.path("reasoning_effort").asText());
            assertFalse(request.has("reasoning")); // Chat Completions uses the top-level field.
        }
    }

    @Test public void defaultOmitsParameterAndBlankUsesMedium() throws Exception {
        assertFalse(request(config("gpt-6-luna", "reasoning_effort=default\n")).has("reasoning_effort"));
        assertEquals("medium", request(config("gpt-6-luna", "reasoning_effort=  \n"))
                .path("reasoning_effort").asText());
    }

    @Test public void normalizesCaseAndWhitespaceRegardlessOfSystemLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("high", request(config("gpt-6-luna", "reasoning_effort= HIGH  \n"))
                    .path("reasoning_effort").asText());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void preservesLegacyNonReasoningModelCompatibility() throws Exception {
        for (String model : new String[] {"gpt-4o-mini", "gpt-4o-2024-08-06", "chatgpt-4o-latest", "gpt-4.1-mini", "gpt-3.5-turbo"}) {
            JsonNode request = request(config(model, "reasoning_effort=medium\n"));
            assertFalse(model, request.has("reasoning_effort"));
            assertEquals(model, request.path("model").asText());
        }
    }

    @Test public void supportsReasoningSnapshotsAndModelSpecificMinimalValue() throws Exception {
        assertEquals("high", request(config("o3-2025-04-16", "reasoning_effort=high\n"))
                .path("reasoning_effort").asText());
        assertEquals("minimal", request(config("gpt-5", "reasoning_effort=minimal\n"))
                .path("reasoning_effort").asText());
    }

    @Test public void rejectsInvalidEffortWithoutExposingItsValueOrApiKey() throws Exception {
        for (String invalid : new String[] {"typo-secret", "maximum", "\"high\"", "high low"}) {
            IOException error = assertThrows(IOException.class,
                    () -> config("gpt-6-luna", "reasoning_effort=" + invalid + "\n"));
            assertTrue(error.getMessage().contains("reasoning_effort"));
            assertFalse(error.getMessage().contains("test-secret"));
            assertFalse(error.getMessage().contains("typo-secret"));
        }
    }

    @Test public void stepTwoReloadsChangedEffortAndReportsEffectiveSetting() throws Exception {
        Path app = temporary.newFolder().toPath();
        String original = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", app.toString());
            Path input = app.resolve("input.xliff");
            Files.write(app.resolve("input.xliff_source_text.txt"), "<source id=\"1\">text</source>\n".getBytes(StandardCharsets.UTF_8));
            for (String effort : new String[] {"medium", "high"}) {
                Files.write(app.resolve("Config.properties"), ("api_key=test-secret\nmodel_version=gpt-6-luna\nreasoning_effort=" + effort + "\n").getBytes(StandardCharsets.UTF_8));
                assertEquals(1, ChatGPTClient.getTotalLines(input.toString()));
                assertEquals("gpt-6-luna / " + effort, ChatGPTClient.getReasoningDescription());
            }
        } finally {
            System.setProperty("user.dir", original);
        }
    }

    private TranslationConfig config(String model, String setting) throws Exception {
        Path app = temporary.newFolder().toPath();
        Files.write(app.resolve("Config.properties"), ("api_key=test-secret\nmodel_version=" + model + "\n" + setting).getBytes(StandardCharsets.UTF_8));
        return TranslationConfig.load(app, app);
    }

    private static JsonNode request(TranslationConfig config) throws IOException {
        return new ObjectMapper().readTree(ChatGPTClient.createRequestBody(config, "translate these records"));
    }
}
