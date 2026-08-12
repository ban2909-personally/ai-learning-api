package com.ailearning.platform.identity.application;

import com.ailearning.platform.identity.api.AuthResponse;

import java.time.Duration;

public record AuthResult(AuthResponse response, String refreshToken, Duration refreshTokenTtl) {
}
