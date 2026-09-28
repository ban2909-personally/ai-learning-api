package com.ailearning.platform.community.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class DirectChatPolicyTest {
    private final DirectChatPolicy policy = new DirectChatPolicy();

    @Test
    void messagesAreTrimmedBoundedAndClientIdIsMandatory() {
        assertEquals("Hello", policy.body(" Hello "));
        assertEquals(2000, policy.body("x".repeat(2000)).length());
        assertThrows(BusinessException.class, () -> policy.body(null));
        assertThrows(BusinessException.class, () -> policy.body(" "));
        assertThrows(BusinessException.class, () -> policy.body("x".repeat(2001)));
        assertThrows(BusinessException.class, () -> policy.requireClientId(null));
        assertDoesNotThrow(() -> policy.requireClientId(UUID.randomUUID()));
    }

    @Test
    void selfChatAndNonActiveRequestsCannotSend() {
        UUID actor = UUID.randomUUID();
        assertThrows(BusinessException.class, () -> policy.requireDifferentUsers(actor, actor));
        assertDoesNotThrow(() -> policy.requireDifferentUsers(actor, UUID.randomUUID()));
        for (String status : new String[] {null, "REQUEST", "DECLINED"})
            assertThrows(BusinessException.class, () -> policy.requireSend(status));
        assertDoesNotThrow(() -> policy.requireSend("ACTIVE"));
    }
}
