package com.ailearning.platform.identity.api.contract;

public record RegisterCommand(String email, String password, String displayName) {
}
