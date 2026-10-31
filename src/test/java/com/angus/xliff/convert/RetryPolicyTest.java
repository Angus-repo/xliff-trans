package com.angus.xliff.convert;

import static org.junit.Assert.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

public class RetryPolicyTest {
    @Test public void retries520ThenReturnsSuccessWithOneDelay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        String result = RetryPolicy.execute(() -> {
            if (calls.incrementAndGet() == 1) return ChatGPTClient.readResponse(520, "<html>test-secret</html>");
            return "translated";
        }, delays::add, notices::add);
        assertEquals("translated", result);
        assertEquals(2, calls.get());
        assertEquals(Arrays.asList(2000L), delays);
        assertTrue(notices.get(0).contains("520"));
        assertFalse(notices.get(0).contains("test-secret"));
    }

    @Test public void stopsAfterThreeRetriesAndUsesExponentialBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        IOException error = assertThrows(IOException.class, () -> RetryPolicy.execute(() -> {
            calls.incrementAndGet();
            return ChatGPTClient.readResponse(520, "test-secret");
        }, delays::add, ignored -> {}));
        assertEquals(4, calls.get());
        assertEquals(Arrays.asList(2000L, 4000L, 8000L), delays);
        assertTrue(error.getMessage().contains("520"));
        assertFalse(error.getMessage().contains("test-secret"));
    }

    @Test public void retriesAllTransientHttpStatuses() throws Exception {
        for (int status : new int[] {408, 409, 429, 500, 502, 503, 504, 520, 522, 524}) {
            AtomicInteger calls = new AtomicInteger();
            assertEquals("ok", RetryPolicy.execute(() -> {
                if (calls.incrementAndGet() == 1) return ChatGPTClient.readResponse(status, "{}");
                return "ok";
            }, ignored -> {}, ignored -> {}));
            assertEquals(2, calls.get());
        }
    }

    @Test public void doesNotRetryConfigurationAuthenticationOrQuotaErrors() {
        for (int status : new int[] {400, 401, 403, 404, 422, 429}) {
            AtomicInteger calls = new AtomicInteger();
            assertThrows(IOException.class, () -> RetryPolicy.execute(() -> {
                calls.incrementAndGet();
                return ChatGPTClient.readResponse(status, "{\"error\":{\"code\":\"insufficient_quota\"}}");
            }, ignored -> fail("Must not wait"), ignored -> fail("Must not retry")));
            assertEquals(1, calls.get());
        }
    }

    @Test public void retriesNetworkTimeoutWithoutEchoingExceptionDetails() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> notices = new ArrayList<>();
        assertEquals("ok", RetryPolicy.execute(() -> {
            if (calls.incrementAndGet() == 1) throw new SocketTimeoutException("test-secret");
            return "ok";
        }, ignored -> {}, notices::add));
        assertFalse(notices.get(0).contains("test-secret"));
    }

    @Test public void doesNotRetryCertificateFailure() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(SSLHandshakeException.class, () -> RetryPolicy.execute(() -> {
            calls.incrementAndGet();
            throw new SSLHandshakeException("certificate failure");
        }, ignored -> fail("Must not wait"), ignored -> {}));
        assertEquals(1, calls.get());
    }

    @Test public void respectsRetryAfterSeconds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        RetryPolicy.execute(() -> {
            if (calls.incrementAndGet() == 1) return ChatGPTClient.readResponse(429, "{}", "15");
            return "ok";
        }, delays::add, ignored -> {});
        assertEquals(Arrays.asList(15000L), delays);
    }

    @Test public void doesNotRetryEarlyWhenServerRequestsAnExcessiveWait() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> RetryPolicy.execute(() -> {
            calls.incrementAndGet();
            return ChatGPTClient.readResponse(503, "{}", "121");
        }, ignored -> fail("Must stop instead of shortening Retry-After"), ignored -> {}));
        assertEquals(1, calls.get());
    }

    @Test public void parsesRetryAfterDateAndIgnoresInvalidValues() {
        long now = 1700000000000L;
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now + 30000).atZone(ZoneOffset.UTC));
        assertEquals(30000, RetryPolicy.retryAfterMillis(date, now));
        assertEquals(0, RetryPolicy.retryAfterMillis("-1", now));
        assertEquals(0, RetryPolicy.retryAfterMillis("invalid", now));
        assertEquals(0, RetryPolicy.retryAfterMillis(null, now));
        assertEquals(Long.MAX_VALUE, RetryPolicy.retryAfterMillis(Long.toString(Long.MAX_VALUE), now));
    }

    @Test public void interruptionStopsRetriesAndRestoresInterruptFlag() {
        try {
            assertThrows(InterruptedException.class, () -> RetryPolicy.execute(
                    () -> ChatGPTClient.readResponse(520, ""),
                    ignored -> { throw new InterruptedException(); }, ignored -> {}));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void incompleteOrFilteredResponsesAreNotCommittedOrRetried() {
        for (String reason : new String[] {"length", "content_filter"}) {
            assertThrows(IOException.class, () -> RetryPolicy.execute(() -> ChatGPTClient.readResponse(200,
                    "{\"choices\":[{\"finish_reason\":\"" + reason + "\",\"message\":{\"content\":\"partial\"}}]}"),
                    ignored -> fail("Must not retry"), ignored -> {}));
        }
    }
}
