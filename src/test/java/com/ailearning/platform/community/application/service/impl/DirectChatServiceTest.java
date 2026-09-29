package com.ailearning.platform.community.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.community.api.contract.*;
import com.ailearning.platform.community.application.port.out.DirectChatStore;
import com.ailearning.platform.identity.api.contract.UserView;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

class DirectChatServiceTest {
    private final DirectChatStore store = mock(DirectChatStore.class);
    private final AccountAccess accounts = mock(AccountAccess.class);
    private final DirectChatService service = new DirectChatService(store, accounts);
    private final UUID actor = UUID.randomUUID(), peer = UUID.randomUUID(), id = UUID.randomUUID();

    private DirectConversationView conversation(String status) {
        return new DirectConversationView(
                id, peer, "Peer", actor, status, null, Instant.EPOCH, 0, 0);
    }

    @Test
    void startUsesIdentityBoundaryAndNormalizedContent() {
        UUID client = UUID.randomUUID();
        when(accounts.findActiveByEmail("peer@example.com"))
                .thenReturn(
                        Optional.of(
                                new UserView(peer, "peer@example.com", "Peer", Set.of("STUDENT"))));
        when(store.start(actor, peer, client, "Hello")).thenReturn(conversation("REQUEST"));
        assertEquals(
                "REQUEST", service.start(actor, " peer@example.com ", client, " Hello ").status());
        verify(accounts).requireActive(actor);
        verify(store).start(actor, peer, client, "Hello");
        assertThrows(
                BusinessException.class,
                () -> service.start(null, "peer@example.com", client, "Hello"));
        assertThrows(BusinessException.class, () -> service.start(actor, " ", client, "Hello"));
        assertThrows(
                BusinessException.class,
                () -> service.start(actor, "missing@example.com", client, "Hello"));
    }

    @Test
    void idBasedChatKeepsActiveAccountAndClientIdChecksWithoutEmailLookup() {
        UUID client = UUID.randomUUID();
        when(store.start(actor, peer, client, "Hello")).thenReturn(conversation("REQUEST"));
        assertEquals("REQUEST", service.startWithPeer(actor, peer, client, " Hello ").status());
        verify(accounts).requireActive(peer);
        verify(accounts, never()).findActiveByEmail(any());
        when(store.findWithPeer(actor, peer)).thenReturn(Optional.of(conversation("ACTIVE")));
        assertTrue(service.findWithPeer(actor, peer).isPresent());
        assertThrows(
                BusinessException.class,
                () -> service.startWithPeer(actor, actor, client, "Hello"));
        assertThrows(
                BusinessException.class, () -> service.startWithPeer(actor, null, client, "Hello"));
        assertThrows(BusinessException.class, () -> service.findWithPeer(actor, null));
        assertThrows(
                BusinessException.class, () -> service.startWithPeer(actor, peer, null, "Hello"));
        assertThrows(BusinessException.class, () -> service.findWithPeer(null, peer));
    }

    @Test
    void participantAndActivePeerAreRequiredBeforeCallingSendPort() {
        when(store.conversation(actor, id)).thenReturn(Optional.of(conversation("REQUEST")));
        assertThrows(
                BusinessException.class, () -> service.send(actor, id, UUID.randomUUID(), "Hello"));
        verify(store, never()).send(any(), any(), any(), any());
        when(store.conversation(actor, id)).thenReturn(Optional.of(conversation("ACTIVE")));
        UUID client = UUID.randomUUID();
        service.send(actor, id, client, " Hello ");
        verify(accounts, atLeastOnce()).requireActive(peer);
        verify(store).send(actor, id, client, "Hello");
    }

    @Test
    void cursorsDecisionAndInboxInputRejectInvalidValues() {
        when(store.conversation(actor, id)).thenReturn(Optional.of(conversation("ACTIVE")));
        assertThrows(BusinessException.class, () -> service.messages(actor, id, 1L, 2L));
        assertThrows(BusinessException.class, () -> service.messages(actor, id, -1L, null));
        assertThrows(BusinessException.class, () -> service.messages(actor, id, null, 0L));
        assertThrows(BusinessException.class, () -> service.markRead(actor, id, -1));
        assertThrows(BusinessException.class, () -> service.decide(actor, id, true));
        assertThrows(BusinessException.class, () -> service.inbox(actor, "invalid", 0));
        assertThrows(BusinessException.class, () -> service.inbox(actor, "all", 1001));
        when(store.markRead(actor, id, 2)).thenReturn(true);
        assertDoesNotThrow(() -> service.markRead(actor, id, 2));
        assertThrows(BusinessException.class, () -> service.markRead(actor, id, 3));
    }
}
