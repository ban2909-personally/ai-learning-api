package com.ailearning.platform.identity.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.identity.api.contract.PublicProfile;
import com.ailearning.platform.identity.application.port.out.PublicProfileStore;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class PublicProfileServiceTest {
    private final PublicProfileStore store = mock(PublicProfileStore.class);
    private final PublicProfileService service = new PublicProfileService(store);

    @Test
    void emptyAndInvalidQueryNeverReadTheDirectory() {
        assertTrue(service.search(null, 0).isEmpty());
        assertTrue(service.search(" ", 0).isEmpty());
        assertThrows(BusinessException.class, () -> service.search("a".repeat(121), 0));
        assertThrows(BusinessException.class, () -> service.search("h", -1));
        assertThrows(BusinessException.class, () -> service.search("h", 101));
        verifyNoInteractions(store);
    }

    @Test
    void trimsQueryAndUnavailableProfileHasTheSameNotFoundResponse() {
        UUID id = UUID.randomUUID();
        var profile = new PublicProfile(id, "Hà Anh");
        when(store.search("h", 2)).thenReturn(List.of(profile));
        when(store.findActive(id)).thenReturn(Optional.of(profile));
        assertEquals(List.of(profile), service.search(" h ", 2));
        assertEquals(profile, service.profile(id));
        var missing =
                assertThrows(BusinessException.class, () -> service.profile(UUID.randomUUID()));
        assertEquals("profile_not_found", missing.code());
    }
}
