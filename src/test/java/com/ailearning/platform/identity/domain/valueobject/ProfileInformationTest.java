package com.ailearning.platform.identity.domain.valueobject;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;

class ProfileInformationTest {
    @Test
    void normalizesOptInInformationWithoutInterpretingMarkup() {
        var info =
                new ProfileInformation(
                        " <b>Hello</b> ", null, " https://example.test/path ", "ocean");
        assertEquals("<b>Hello</b>", info.bio());
        assertEquals("", info.location());
        assertEquals("https://example.test/path", info.website());
        assertDoesNotThrow(() -> new ProfileInformation(null, null, null, "forest"));
    }

    @Test
    void rejectsUnsafeUrlsCredentialsAndUnknownThemes() {
        for (String website :
                List.of(
                        "javascript:alert(1)",
                        "data:text/html,Hi",
                        "/relative",
                        "https://user:pass@example.test",
                        "http://",
                        "https://example.test/ bad"))
            assertThrows(
                    BusinessException.class,
                    () -> new ProfileInformation("", "", website, "aurora"));
        assertThrows(BusinessException.class, () -> new ProfileInformation("", "", "", null));
        assertThrows(BusinessException.class, () -> new ProfileInformation("", "", "", "custom"));
    }

    @Test
    void boundsAllFieldsAndRejectsControlCharacters() {
        assertThrows(
                BusinessException.class,
                () -> new ProfileInformation("a".repeat(401), "", "", "sunset"));
        assertThrows(
                BusinessException.class,
                () -> new ProfileInformation("", "a".repeat(121), "", "sunset"));
        assertThrows(
                BusinessException.class,
                () -> new ProfileInformation("", "", "a".repeat(501), "sunset"));
        assertThrows(
                BusinessException.class,
                () -> new ProfileInformation("Hi\u0000there", "", "", "sunset"));
    }
}
