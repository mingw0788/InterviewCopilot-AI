package com.interviewcopilot.business.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class RequestIds {
    private static final String REQUEST_ID_ATTRIBUTE = RequestIds.class.getName() + ".requestId";

    private RequestIds() {
    }

    public static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String requestId) {
            return requestId;
        }

        String supplied = request.getHeader("X-Request-Id");
        String resolved = isValid(supplied) ? supplied : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, resolved);
        return resolved;
    }

    private static boolean isValid(String value) {
        return value != null
                && !value.isBlank()
                && value.codePointCount(0, value.length()) <= 128
                && value.codePoints().noneMatch(Character::isISOControl);
    }
}
