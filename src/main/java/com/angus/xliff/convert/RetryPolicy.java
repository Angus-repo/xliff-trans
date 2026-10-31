package com.angus.xliff.convert;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;

final class RetryPolicy {
    static final int MAX_RETRIES = 3;
    static final long MAX_RETRY_DELAY_MILLIS = 120000;
    interface Attempt { String execute() throws IOException; }
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    static String execute(Attempt attempt, Sleeper sleeper, Consumer<String> notice) throws IOException, InterruptedException {
        ResourceBundle messages = ResourceBundle.getBundle("MessagesBundle");
        for (int retries = 0; ; retries++) {
            try {
                return attempt.execute();
            } catch (IOException error) {
                ApiFailure api = error instanceof ApiFailure ? (ApiFailure) error : null;
                if ((api != null && !api.retryable) || error instanceof SSLException) throw error;
                long delay = Math.max(2000L << retries, api == null ? 0 : api.retryAfterMillis);
                String reason = api == null ? messages.getString("error_network") : api.getMessage();
                if (retries >= MAX_RETRIES || delay > MAX_RETRY_DELAY_MILLIS) {
                    throw new IOException(messages.getString("error_retry_exhausted") + " " + reason);
                }
                notice.accept(MessageFormat.format(messages.getString("retry_batch"),
                        reason, (delay + 999) / 1000, retries + 1, MAX_RETRIES));
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
    }

    static long retryAfterMillis(String value, long nowMillis) {
        if (value == null) return 0;
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 0) return 0;
            return seconds > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : seconds * 1000;
        } catch (NumberFormatException ignored) {
            try {
                long time = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                return Math.max(0, time - nowMillis);
            } catch (DateTimeParseException invalid) {
                return 0;
            }
        }
    }
}
