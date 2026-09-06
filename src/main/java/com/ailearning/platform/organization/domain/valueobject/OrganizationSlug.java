package com.ailearning.platform.organization.domain.valueobject;

import java.util.Objects;

public record OrganizationSlug(String value) {
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 80;
    private static final String URL_SAFE_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    public OrganizationSlug {
        Objects.requireNonNull(value, "value is required");
        value = value.trim();
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || !value.matches(URL_SAFE_PATTERN)) {
            throw new IllegalArgumentException(
                    "organization slug must be 3 to 80 lowercase URL-safe characters"
            );
        }
    }
}
