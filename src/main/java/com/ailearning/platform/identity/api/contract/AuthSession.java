package com.ailearning.platform.identity.api.contract;

import java.time.Duration;

public record AuthSession(String accessToken, long expiresIn, UserView user, String refreshToken, Duration refreshTokenTtl) {
}
