package com.dibyanshusinha.apiserv.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationId.class.getName() + ".value";

    private static final int MAX_LENGTH = 128;
    private static final Pattern ALLOWED_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");

    private CorrelationId() {
    }

    public static String resolve(String incomingCorrelationId) {
        if (isValid(incomingCorrelationId)) {
            return incomingCorrelationId.trim();
        }
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String correlationId) {
        if (correlationId == null) {
            return false;
        }

        String value = correlationId.trim();
        return !value.isBlank()
                && value.length() <= MAX_LENGTH
                && ALLOWED_VALUE.matcher(value).matches();
    }
}
