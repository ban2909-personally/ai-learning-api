package com.ailearning.platform.identity.api.contract;

import java.util.UUID;

/** Only opt-in public information; never account credentials, email or roles. */
public record SocialProfileDetails(
        UUID id,
        String displayName,
        String bio,
        String location,
        String website,
        String coverTheme) {}
