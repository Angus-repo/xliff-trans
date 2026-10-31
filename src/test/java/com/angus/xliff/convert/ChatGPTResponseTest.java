package com.angus.xliff.convert;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class ChatGPTResponseTest {
    @Test public void acceptsValidTranslation() throws Exception {
        assertEquals("繁體中文\n第二段", ChatGPTClient.readResponse(200,
                "{\"choices\":[{\"message\":{\"content\":\"繁體中文\\n第二段\"}}]}"));
    }

    @Test public void reportsAuthenticationByHttpStatusWithoutExposingResponse() {
        IOException error = assertThrows(IOException.class, () -> ChatGPTClient.readResponse(401,
                "{\"error\":{\"message\":\"Incorrect API key: test-secret\"}}"));
        assertTrue(error.getMessage().contains("401"));
        assertFalse(error.getMessage().contains("test-secret"));
    }

    @Test public void badRequestIsNotReportedAsAnInvalidKey() {
        IOException error = assertThrows(IOException.class, () -> ChatGPTClient.readResponse(400,
                "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"test-secret\"}}"));
        assertTrue(error.getMessage().contains("400"));
        assertFalse(error.getMessage().contains("api_key"));
        assertFalse(error.getMessage().contains("test-secret"));
    }

    @Test public void distinguishesQuotaFromRateLimit() {
        IOException quota = assertThrows(IOException.class, () -> ChatGPTClient.readResponse(429,
                "{\"error\":{\"code\":\"insufficient_quota\"}}"));
        IOException rate = assertThrows(IOException.class, () -> ChatGPTClient.readResponse(429,
                "{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));
        assertNotEquals(quota.getMessage(), rate.getMessage());
        assertTrue(quota.getMessage().contains("429"));
        assertTrue(rate.getMessage().contains("429"));
    }

    @Test public void reportsPermissionAndServerErrorsWithoutResponseBody() {
        for (int status : new int[] {403, 500, 503}) {
            IOException error = assertThrows(IOException.class, () -> ChatGPTClient.readResponse(status, "test-secret"));
            assertTrue(error.getMessage().contains(Integer.toString(status)));
            assertFalse(error.getMessage().contains("test-secret"));
        }
    }

    @Test public void rejectsMissingEmptyAndMalformedResponsesWithoutEchoingContent() {
        for (String body : new String[] {"test-secret", "null", "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":null}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"}) {
            IOException error = assertThrows(IOException.class, () -> ChatGPTClient.readResponse(200, body));
            assertFalse(error.getMessage().contains("test-secret"));
        }
    }
}
