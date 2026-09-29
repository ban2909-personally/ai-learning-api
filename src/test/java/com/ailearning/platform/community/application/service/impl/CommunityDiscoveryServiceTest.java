package com.ailearning.platform.community.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.identity.api.contract.PublicProfile;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.api.usecase.access.PublicProfileLookup;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

class CommunityDiscoveryServiceTest {
    private final CommunityStore spaces = mock(CommunityStore.class);
    private final PublicProfileLookup profiles = mock(PublicProfileLookup.class);
    private final AccountAccess accounts = mock(AccountAccess.class);
    private final CommunityDiscoveryService service =
            new CommunityDiscoveryService(spaces, profiles, accounts);

    @Test
    void blankQueryDoesNotEnumerateAndInvalidInputsDoNotReachPorts() {
        assertTrue(service.search(null, "  ", 0).people().isEmpty());
        assertThrows(BusinessException.class, () -> service.search(null, "a".repeat(121), 0));
        assertThrows(BusinessException.class, () -> service.search(null, "h", -1));
        assertThrows(BusinessException.class, () -> service.search(null, "h", 101));
        verifyNoInteractions(spaces, profiles);
    }

    @Test
    void oneCharacterUsesIdentityBoundaryAndTruncatesSentinel() {
        UUID viewer = UUID.randomUUID();
        when(spaces.searchSpaces("h", viewer, 0, 21)).thenReturn(List.of());
        when(profiles.search("h", 0))
                .thenReturn(
                        IntStream.range(0, 21)
                                .mapToObj(
                                        index ->
                                                new PublicProfile(UUID.randomUUID(), "Hà " + index))
                                .toList());
        var found = service.search(viewer, " h ", 0);
        assertEquals(20, found.people().size());
        assertTrue(found.peopleHasMore());
        assertFalse(found.spacesHasMore());
        verify(accounts).requireActive(viewer);
        verify(profiles).search("h", 0);
    }

    @Test
    void inactiveViewerIsRejectedBeforeSearchingAndProfileUsesPublicBoundary() {
        UUID viewer = UUID.randomUUID(), id = UUID.randomUUID();
        doThrow(
                        new BusinessException(
                                "inactive",
                                com.ailearning.platform.sharedkernel.error.ErrorType.FORBIDDEN,
                                "Inactive"))
                .when(accounts)
                .requireActive(viewer);
        assertThrows(BusinessException.class, () -> service.search(viewer, "h", 0));
        verifyNoInteractions(spaces, profiles);
        var publicProfile = new PublicProfile(id, "Hà Anh");
        when(profiles.profile(id)).thenReturn(publicProfile);
        assertEquals(publicProfile, service.profile(null, id));
    }
}
