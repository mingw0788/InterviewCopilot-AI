package com.interviewcopilot.business.interview.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DomainChecks {
    private DomainChecks() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new DomainValidationException(field + " is required");
        }
        return value;
    }

    static String requiredText(String value, String field, int maxLength) {
        if (value == null) {
            throw new DomainValidationException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new DomainValidationException(field + " must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new DomainValidationException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    static List<String> textList(
            List<String> values,
            String field,
            int minItems,
            int maxItems,
            int maxItemLength
    ) {
        return textList(values, field, minItems, maxItems, maxItemLength, DuplicatePolicy.ALLOW);
    }

    static List<String> uniqueTextList(
            List<String> values,
            String field,
            int minItems,
            int maxItems,
            int maxItemLength
    ) {
        return textList(values, field, minItems, maxItems, maxItemLength, DuplicatePolicy.REJECT);
    }

    static List<String> caseInsensitiveDistinctTextList(
            List<String> values,
            String field,
            int minItems,
            int maxItems,
            int maxItemLength
    ) {
        return textList(values, field, minItems, maxItems, maxItemLength, DuplicatePolicy.DEDUPLICATE_CASE_INSENSITIVE);
    }

    private static List<String> textList(
            List<String> values,
            String field,
            int minItems,
            int maxItems,
            int maxItemLength,
            DuplicatePolicy duplicatePolicy
    ) {
        if (values == null) {
            throw new DomainValidationException(field + " is required");
        }
        if (values.size() > maxItems) {
            throw new DomainValidationException(field + " must not contain more than " + maxItems + " items");
        }

        List<String> normalized = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String item = requiredText(value, field + " item", maxItemLength);
            if (duplicatePolicy == DuplicatePolicy.ALLOW) {
                normalized.add(item);
                continue;
            }
            String key = duplicatePolicy == DuplicatePolicy.DEDUPLICATE_CASE_INSENSITIVE
                    ? item.toLowerCase(Locale.ROOT)
                    : item;
            if (seen.add(key)) {
                normalized.add(item);
            } else if (duplicatePolicy == DuplicatePolicy.REJECT) {
                throw new DomainValidationException(field + " must contain unique items");
            }
        }
        if (normalized.size() < minItems) {
            throw new DomainValidationException(field + " must contain at least " + minItems + " item(s)");
        }
        return List.copyOf(normalized);
    }

    private enum DuplicatePolicy {
        ALLOW,
        REJECT,
        DEDUPLICATE_CASE_INSENSITIVE
    }

    static Instant notBefore(Instant value, Instant lowerBound, String field) {
        required(value, field);
        required(lowerBound, "lowerBound");
        if (value.isBefore(lowerBound)) {
            throw new DomainValidationException(field + " must not be before the preceding domain event");
        }
        return value;
    }
}
