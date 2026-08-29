package com.ailearning.platform.identity.application.port.out;

public interface TokenDigest {
    String digest(String token);
}
