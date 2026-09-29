package com.ailearning.platform.community.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.community.domain.model.Friendship;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class FriendshipPolicyTest {
    private final FriendshipPolicy policy = new FriendshipPolicy();
    private final UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID();

    @Test
    void pendingRequiresExplicitRecipientAcceptance() {
        var pending = new Friendship(sender, "PENDING");
        assertDoesNotThrow(() -> policy.requireRequest(sender, pending));
        assertThrows(BusinessException.class, () -> policy.requireRequest(recipient, pending));
        assertThrows(BusinessException.class, () -> policy.requireAccept(sender, pending));
        assertDoesNotThrow(() -> policy.requireAccept(recipient, pending));
        assertThrows(
                BusinessException.class,
                () -> policy.requireAccept(recipient, new Friendship(sender, "ACCEPTED")));
        assertDoesNotThrow(() -> policy.requireRequest(sender, new Friendship(sender, "ACCEPTED")));
    }

    @Test
    void viewerRelativeRelationshipNeverExposesRequestsToAnonymous() {
        var pending = new Friendship(sender, "PENDING");
        assertEquals("SELF", policy.relationship(sender, sender, null));
        assertEquals("NONE", policy.relationship(null, recipient, pending));
        assertEquals("NONE", policy.relationship(sender, recipient, null));
        assertEquals("OUTGOING", policy.relationship(sender, recipient, pending));
        assertEquals("INCOMING", policy.relationship(recipient, sender, pending));
        assertEquals(
                "FRIENDS",
                policy.relationship(recipient, sender, new Friendship(sender, "ACCEPTED")));
        assertThrows(BusinessException.class, () -> policy.requireDifferentUsers(sender, sender));
        assertThrows(BusinessException.class, () -> policy.requireDifferentUsers(sender, null));
    }
}
