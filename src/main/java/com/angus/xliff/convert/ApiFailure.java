package com.angus.xliff.convert;

import java.io.IOException;

final class ApiFailure extends IOException {
    private static final long serialVersionUID = 1L;
    final boolean retryable;
    final long retryAfterMillis;

    ApiFailure(String message, boolean retryable, long retryAfterMillis) {
        super(message);
        this.retryable = retryable;
        this.retryAfterMillis = retryAfterMillis;
    }
}
