package com.ailearning.platform.identity.application.port.out;

import com.ailearning.platform.identity.domain.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserStore {
    boolean existsById(UUID id);
    boolean existsByEmail(String email);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    User createStudent(UUID id, String email, String passwordHash, String displayName, Instant now);
}
