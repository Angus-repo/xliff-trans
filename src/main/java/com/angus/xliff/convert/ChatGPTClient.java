package com.angus.xliff.convert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatGPTClient {
    private static final Logger logger = LoggerFactory.getLogger(ChatGPTClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static TranslationConfig config;
    private static TranslationProgress progress;

    public static int getTotalLines(String filePath) throws IOException {
        config = null;
        progress = null;
        TranslationConfig loaded = TranslationConfig.load();
        TranslationProgress restored = TranslationProgress.open(filePath);
        config = loaded;
        progress = restored;
        return progress.total();
    }

    public static int getCompletedCount() { return progress == null ? 0 : progress.completedCount(); }
    public static String getConfigurationPath() { return config == null ? "" : config.getPath().toString(); }

    public static int processNextBatch(String filePath) throws Exception {
        return processNextBatch(filePath, message -> logger.info("{}", message));
    }

    @SuppressWarnings("deprecation")
    public static int processNextBatch(String filePath, Consumer<String> notice) throws Exception {
        if (config == null || progress == null || !progress.isFor(filePath)) {
            throw new IOException("Start Step 2 before processing a batch.");
        }
        if (progress.completedCount() == progress.total()) return 0;
        // Freeze this batch's request across retries. Disk writes happen only after success.
        LinkedHashMap<String, String> batch = progress.nextBatch(config.getBatchSize());
        String combinedContent = config.readPrefix() + String.join("\n", batch.values());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getModel());
        body.putArray("messages").addObject().put("role", "user").put("content", combinedContent);
        String requestBody = mapper.writeValueAsString(body);
        RequestConfig timeouts = RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(180)).build();
        try (CloseableHttpClient client = HttpClients.custom().disableAutomaticRetries()
                .setDefaultRequestConfig(timeouts).build()) {
            return processBatch(progress, batch, () -> request(client, requestBody, config.getApiKey()),
                    Thread::sleep, notice);
        }
    }

    static int processBatch(TranslationProgress progress, LinkedHashMap<String, String> batch,
                            RetryPolicy.Attempt request, RetryPolicy.Sleeper sleeper, Consumer<String> notice)
                            throws IOException, InterruptedException {
        String response = RetryPolicy.execute(request, sleeper, notice);
        progress.commit(batch, response);
        return batch.size();
    }

    @SuppressWarnings("deprecation")
    private static String request(CloseableHttpClient client, String body, String key) throws IOException {
        HttpPost request = new HttpPost(API_URL);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Authorization", "Bearer " + key);
        request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
        try (CloseableHttpResponse response = client.execute(request)) {
            StringBuilder result = new StringBuilder();
            if (response.getEntity() != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) result.append(line).append('\n');
                }
            }
            Header retryAfter = response.getFirstHeader("Retry-After");
            return readResponse(response.getCode(), result.toString(),
                    retryAfter == null ? null : retryAfter.getValue());
        }
    }

    static String readResponse(int status, String body) throws IOException {
        return readResponse(status, body, null);
    }

    static String readResponse(int status, String body, String retryAfter) throws IOException {
        ResourceBundle messages = ResourceBundle.getBundle("MessagesBundle");
        long delay = RetryPolicy.retryAfterMillis(retryAfter, System.currentTimeMillis());
        if (status == 401) throw failure(messages, "error_invalid_key", status, false, 0);
        if (status == 403) throw failure(messages, "error_api_forbidden", status, false, 0);
        if (status >= 500 && status <= 599) throw failure(messages, "error_api_transient", status, true, delay);
        if (status == 408 || status == 409) throw failure(messages, "error_api_transient", status, true, delay);
        JsonNode json = null;
        try {
            json = new ObjectMapper().readTree(body);
        } catch (IOException ignored) {
            // Never expose parser errors or API error.message, which can echo secrets.
        }
        if (status == 429) {
            String code = json == null ? "" : json.path("error").path("code").asText();
            boolean quota = "insufficient_quota".equals(code) || "billing_hard_limit_reached".equals(code)
                    || "organization_usage_limit_exceeded".equals(code);
            throw failure(messages, quota ? "error_api_quota" : "error_rate_limit", status, !quota, delay);
        }
        if (status < 200 || status >= 300) throw failure(messages, "error_api_request", status, false, 0);
        JsonNode content = json == null ? null : json.path("choices").path(0).path("message").path("content");
        String finish = json == null ? "" : json.path("choices").path(0).path("finish_reason").asText();
        if ("length".equals(finish) || "content_filter".equals(finish)) {
            throw failure(messages, "error_batch_incomplete", status, false, 0);
        }
        if (content == null || !content.isTextual() || content.asText().trim().isEmpty()) {
            throw failure(messages, "error_null_response", status, false, 0);
        }
        return content.asText();
    }

    private static ApiFailure failure(ResourceBundle messages, String key, int status, boolean retry, long delay) {
        return new ApiFailure(messages.getString(key) + " (HTTP " + status + ")", retry, delay);
    }
}
