package com.ailearning.platform.flashcard.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.flashcard.application.port.out.FlashcardStore;
import com.ailearning.platform.flashcard.domain.model.*;
import com.ailearning.platform.identity.api.contract.UserView;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.*;

class FlashcardServiceTest {
    private final FlashcardStore store = mock(FlashcardStore.class);
    private final AccountAccess access = mock(AccountAccess.class);
    private final FlashcardService service = new FlashcardService(store, access);
    private final UUID actor = UUID.randomUUID();

    private void role(String role) {
        when(access.requireActive(actor))
                .thenReturn(new UserView(actor, "demo@example.com", "Demo", Set.of(role)));
    }

    @Test
    void guestCannotCreateDeck() {
        role("GUEST");
        assertThrows(
                BusinessException.class,
                () -> service.save(actor, null, "Title", "", List.of(new Flashcard("Q", "A"))));
        verifyNoInteractions(store);
    }

    @Test
    void anotherOwnersDeckIsNotFoundEvenForAdmin() {
        role("ADMIN");
        UUID deck = UUID.randomUUID();
        when(store.find(actor, deck)).thenReturn(Optional.empty());
        assertEquals(
                "deck_not_found",
                assertThrows(BusinessException.class, () -> service.get(actor, deck)).code());
    }

    @Test
    void validatesBothFacesBeforeWriting() {
        role("STUDENT");
        assertThrows(
                BusinessException.class,
                () -> service.save(actor, null, "Title", "", List.of(new Flashcard("Q", " "))));
        verify(store, never()).save(any());
    }

    @Test
    void createsOwnerScopedDeck() {
        role("STUDENT");
        when(store.save(any())).thenAnswer(call -> call.getArgument(0));
        var result =
                service.save(
                        actor, null, " Java ", "", List.of(new Flashcard("JVM?", "Máy ảo Java")));
        assertEquals(actor, result.ownerId());
        assertEquals("Java", result.title());
        assertNotNull(result.id());
    }
}
