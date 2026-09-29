package com.ailearning.platform.identity.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.identity.api.contract.*;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.application.port.out.SocialProfileStore;
import com.ailearning.platform.identity.domain.valueobject.ProfileInformation;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.*;

class SocialProfileServiceTest {
    private final SocialProfileStore store = mock(SocialProfileStore.class);
    private final AccountAccess accounts = mock(AccountAccess.class);
    private final SocialProfileService service = new SocialProfileService(store, accounts);
    private final UUID actor = UUID.randomUUID();

    @Test
    void authorizesOwnUpdateAndReadsThroughOutputPort() {
        var details = new SocialProfileDetails(actor, "Name", "Hi", "", "", "ocean");
        var info = new ProfileInformation("Hi", "", "", "ocean");
        when(store.save(actor, info)).thenReturn(true);
        when(store.findActive(actor)).thenReturn(Optional.of(details));
        assertEquals(
                details, service.update(actor, new ProfileUpdate(" Hi ", null, null, "ocean")));
        verify(accounts).requireActive(actor);
        verify(store).save(actor, info);
    }

    @Test
    void rejectsAnonymousInvalidAndInactiveProfiles() {
        assertThrows(
                BusinessException.class,
                () -> service.update(null, new ProfileUpdate("", "", "", "aurora")));
        assertThrows(
                BusinessException.class,
                () -> service.update(actor, new ProfileUpdate("", "", "javascript:x", "aurora")));
        verify(store, never()).save(any(), any());
        assertThrows(BusinessException.class, () -> service.profile(actor));
        assertThrows(
                BusinessException.class,
                () -> service.update(actor, new ProfileUpdate("", "", "", "aurora")));
    }
}
