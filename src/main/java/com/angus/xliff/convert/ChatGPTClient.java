// File: ChatGPTClient.java

package com.angus.xliff.convert;

import static com.angus.xliff.convert.Constans.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ResourceBundle;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.angus.xliff.swing.XliffSwingUI;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChatGPTClient {

	private static final Logger logger = LoggerFactory.getLogger(ChatGPTClient.class);
	
    private static String API_KEY;
    private static String MODEL_VERSION;
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private static int totalLines = 0;
    private static ResourceBundle config = ResourceBundle.getBundle("Config");

    // Path to prefix_promp.txt
    private static final String PREFIX_PROMP_PATH = "prefix_promp.txt"; // Update this path as needed

    static {
        API_KEY = config.containsKey("api_key") ? config.getString("api_key") : "YOUR_API_KEY";
        MODEL_VERSION = config.containsKey("model_version") ? config.getString("model_version") : "gpt-4o-mini";
    }

    public static int getTotalLines(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath + SOURCE_FILE_NAME_POSTFIX));
        totalLines = lines.size();
        return totalLines;
    }

    @SuppressWarnings("deprecation")
	public static int processNextBatch(String filePath) throws Exception {
        if (API_KEY == null || API_KEY.isEmpty() || "YOUR_API_KEY".equals(API_KEY)) {
            throw new IllegalArgumentException("Invalid API key provided. Please check your configuration.");
        }

        // Read batch_size from Config.properties, default to 50
        int batchSize = 50; // Default value
        if (config.containsKey("batch_size")) {
            try {
                batchSize = Integer.parseInt(config.getString("batch_size"));
            } catch (NumberFormatException e) {
                logger.error("Invalid batch_size in Config.properties. Using default value 50.");
            }
        }

        int startLine = totalLines - batchSize;
        startLine = Math.max(startLine, 0);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(API_URL);
            postRequest.setHeader("Content-Type", "application/json");
            postRequest.setHeader("Authorization", "Bearer " + API_KEY);

            List<String> lines = Files.readAllLines(Paths.get(filePath + SOURCE_FILE_NAME_POSTFIX))
                                      .subList(startLine, totalLines);
            String content = lines.stream().collect(Collectors.joining("\n"));

            // Read prefix_promp.txt
            String prefixPromp = readPrefixPromp(PREFIX_PROMP_PATH);

            // Concatenate prefixPromp with content
            String combinedContent = prefixPromp + content;

            String jsonBody = "{" +
                    "\"model\": \"" + MODEL_VERSION + "\"," +
                    "\"messages\": [{\"role\": \"user\", \"content\": \"" + escapeJson(combinedContent) + "\"}]," +
                    "\"max_tokens\": 4096" +
                    "}";

            StringEntity entity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
            postRequest.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(postRequest);
                 FileWriter targetWriter = new FileWriter(filePath + TARGET_FILE_NAME_POSTFIX, true)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonResponse = mapper.readTree(result.toString());
                if (jsonResponse.has("choices") && jsonResponse.get("choices").size() > 0) {
                    String responseContent = jsonResponse.get("choices").get(0).get("message").get("content").asText();
                    targetWriter.write(responseContent);
                    targetWriter.write(System.lineSeparator());
                } else {
                    throw new IOException("Invalid response from ChatGPT API.");
                }

                totalLines -= batchSize;
                return Math.min(batchSize, lines.size());
            }
        }
    }

    /**
     * Reads the content of prefix_promp.txt from the specified path.
     *
     * @param filePath Path to prefix_promp.txt
     * @return Content of prefix_promp.txt as a String
     * @throws IOException If the file cannot be read
     */
    private static String readPrefixPromp(String filePath) throws IOException {
    	
    	URL resource = ChatGPTClient.class.getClassLoader().getResource(filePath);
        String fullFilePath = resource.getFile();
        File file = new File(fullFilePath);
        if (!file.exists()) {
        	
        	file = new File(filePath);
        	if (!file.exists()) {
        		throw new IOException("prefix_promp.txt not found at: " + file.getAbsolutePath());
        	}
        }
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
