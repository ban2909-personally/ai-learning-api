package com.ailearning.platform.community.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.community.api.contract.*;
import com.ailearning.platform.community.application.port.out.FriendshipStore;
import com.ailearning.platform.identity.api.contract.*;
import com.ailearning.platform.identity.api.usecase.access.*;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class SocialProfileServiceTest {
    private final FriendshipStore store = mock(FriendshipStore.class);
    private final SocialProfileAccess profiles = mock(SocialProfileAccess.class);
    private final AccountAccess accounts = mock(AccountAccess.class);
    private final SocialProfileService service =
            new SocialProfileService(store, profiles, accounts);
    private final UUID actor = UUID.randomUUID(), peer = UUID.randomUUID();

    @Test
    void composesPublicProfileThroughIdentityBoundary() {
        var details = new SocialProfileDetails(peer, "Peer", "", "", "", "aurora");
        when(profiles.profile(peer)).thenReturn(details);
        when(store.summary(null, peer)).thenReturn(new FriendshipSummary("NONE", 1, 0));
        assertEquals(details, service.profile(null, peer).profile());
        verifyNoInteractions(accounts);
        service.profile(actor, peer);
        verify(accounts).requireActive(actor);
        var update = new ProfileUpdate("Hi", "", "", "forest");
        service.update(actor, update);
        verify(profiles).update(actor, update);
    }

    @Test
    void mutationsAuthorizeBeforeCallingOutputPorts() {
        service.request(actor, peer);
        service.accept(actor, peer);
        service.remove(actor, peer);
        verify(store).request(actor, peer);
        verify(store).accept(actor, peer);
        verify(store).remove(actor, peer);
        assertThrows(BusinessException.class, () -> service.request(null, peer));
        assertThrows(BusinessException.class, () -> service.accept(actor, actor));
        assertThrows(BusinessException.class, () -> service.remove(actor, null));
    }

    @Test
    void privateListsHaveBoundedExplicitFilters() {
        service.connections(actor, "incoming", 1);
        verify(store).connections(actor, "incoming", 1);
        assertThrows(BusinessException.class, () -> service.connections(null, "accepted", 0));
        assertThrows(BusinessException.class, () -> service.connections(actor, "all", 0));
        assertThrows(BusinessException.class, () -> service.connections(actor, null, 0));
        assertThrows(BusinessException.class, () -> service.connections(actor, "outgoing", -1));
        assertThrows(BusinessException.class, () -> service.connections(actor, "accepted", 101));
    }
}
