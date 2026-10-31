package com.angus.xliff.convert;

import static com.angus.xliff.convert.Constans.*;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatGPTClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static int totalLines = 0;
    private static TranslationConfig config;

    public static int getTotalLines(String filePath) throws IOException {
        // Reload on every Step 2 invocation, so editing the file takes effect on retry.
        config = null;
        totalLines = 0;
        config = TranslationConfig.load();
        List<String> lines = Files.readAllLines(Paths.get(filePath + SOURCE_FILE_NAME_POSTFIX));
        totalLines = lines.size();
        return totalLines;
    }

    public static String getConfigurationPath() {
        return config == null ? "" : config.getPath().toString();
    }

    @SuppressWarnings("deprecation")
    public static int processNextBatch(String filePath) throws Exception {
        if (config == null) throw new IOException("Start Step 2 before processing a batch.");
        int startLine = Math.max(totalLines - config.getBatchSize(), 0);
        if (totalLines <= 0) return 0;

        List<String> lines = Files.readAllLines(Paths.get(filePath + SOURCE_FILE_NAME_POSTFIX))
                .subList(startLine, totalLines);
        String content = lines.stream().collect(Collectors.joining("\n"));
        String combinedContent = config.readPrefix() + content;

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getModel());
        body.putArray("messages").addObject().put("role", "user").put("content", combinedContent);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(API_URL);
            postRequest.setHeader("Content-Type", "application/json");
            postRequest.setHeader("Authorization", "Bearer " + config.getApiKey());
            postRequest.setEntity(new StringEntity(mapper.writeValueAsString(body), StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                StringBuilder result = new StringBuilder();
                if (response.getEntity() != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) result.append(line);
                    }
                }
                String responseContent = readResponse(response.getCode(), result.toString());
                // Do not create/append output or advance progress when the request fails.
                try (FileWriter targetWriter = new FileWriter(filePath + TARGET_FILE_NAME_POSTFIX, true)) {
                    targetWriter.write(responseContent);
                    targetWriter.write(System.lineSeparator());
                }
                totalLines -= lines.size();
                return lines.size();
            }
        }
    }

    static String readResponse(int status, String body) throws IOException {
        ResourceBundle messages = ResourceBundle.getBundle("MessagesBundle");
        if (status == 401) throw new IOException(messages.getString("error_invalid_key") + " (HTTP 401)");
        if (status == 403) throw new IOException(messages.getString("error_api_forbidden") + " (HTTP 403)");
        JsonNode json = null;
        try {
            json = new ObjectMapper().readTree(body);
        } catch (IOException ignored) {
            // Parser errors can include the response body; never expose them or API error.message.
        }
        if (status == 429) {
            boolean quota = json != null && "insufficient_quota".equals(json.path("error").path("code").asText());
            throw new IOException(messages.getString(quota ? "error_api_quota" : "error_rate_limit") + " (HTTP 429)");
        }
        if (status < 200 || status >= 300) {
            throw new IOException(messages.getString("error_api_request") + " (HTTP " + status + ")");
        }
        JsonNode content = json == null ? null : json.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isTextual() || content.asText().trim().isEmpty()) {
            throw new IOException(messages.getString("error_null_response"));
        }
        return content.asText();
    }
}
