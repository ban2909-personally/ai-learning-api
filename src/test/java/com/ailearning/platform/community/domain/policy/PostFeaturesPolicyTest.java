package com.ailearning.platform.community.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.community.domain.model.*;
import com.ailearning.platform.community.domain.valueobject.PostAppearance;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

class PostFeaturesPolicyTest {
    private final PostFeaturesPolicy policy = new PostFeaturesPolicy();
    private final Instant now = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void absentFeaturesAndReadableAppearanceRemainOptional() {
        assertNull(policy.normalize(null, now));
        var input =
                new PostFeatures(
                        new PostAppearance(" https://example.com/learn ", "#173569", "#ffffff"),
                        null);
        assertEquals(
                "https://example.com/learn",
                policy.normalize(input, now).appearance().attachmentUrl());
        assertNull(
                policy.normalize(new PostFeatures(new PostAppearance(" ", null, null), null), now)
                        .appearance()
                        .attachmentUrl());
    }

    @Test
    void dangerousLinksCredentialsMalformedColorsAndLowContrastAreRejected() {
        for (String link :
                List.of(
                        "javascript:alert(1)",
                        "data:text/plain,x",
                        "https://user:pass@example.com",
                        "https://example.com:99999",
                        "not a URL")) {
            assertThrows(
                    BusinessException.class,
                    () ->
                            policy.normalize(
                                    new PostFeatures(new PostAppearance(link, null, null), null),
                                    now));
        }
        for (PostAppearance appearance :
                List.of(
                        new PostAppearance(null, "#fff", "#000000"),
                        new PostAppearance(null, "#ffffff", null),
                        new PostAppearance(null, "#ffffff", "#eeeeee"))) {
            assertThrows(
                    BusinessException.class,
                    () -> policy.normalize(new PostFeatures(appearance, null), now));
        }
    }

    @Test
    void choicesAreTrimmedDistinctBoundedAndDeadlineCannotBePastOrTooDistant() {
        var poll =
                new PollDefinition(
                        PollKind.ELECTION,
                        " Skill? ",
                        List.of(" Reading ", "Listening"),
                        now.plus(Duration.ofDays(1)));
        assertEquals(
                List.of("Reading", "Listening"),
                policy.normalize(new PostFeatures(null, poll), now).poll().options());
        for (List<String> labels :
                List.of(
                        List.of("A"),
                        List.of("A", " a "),
                        List.of("A", ""),
                        List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"))) {
            assertThrows(
                    BusinessException.class,
                    () ->
                            policy.normalize(
                                    new PostFeatures(
                                            null,
                                            new PollDefinition(
                                                    PollKind.POLL, "Skill?", labels, null)),
                                    now));
        }
        for (Instant deadline : List.of(now, now.minusSeconds(1), now.plus(Duration.ofDays(31)))) {
            assertThrows(
                    BusinessException.class,
                    () ->
                            policy.normalize(
                                    new PostFeatures(
                                            null,
                                            new PollDefinition(
                                                    PollKind.POLL,
                                                    "Skill?",
                                                    List.of("A", "B"),
                                                    deadline)),
                                    now));
        }
    }

    @Test
    void exactClosingInstantRejectsVotesButFutureDeadlineAllowsThem() {
        assertThrows(BusinessException.class, () -> policy.requireOpen(false, now, now));
        assertThrows(BusinessException.class, () -> policy.requireOpen(true, null, now));
        assertDoesNotThrow(() -> policy.requireOpen(false, now.plusSeconds(1), now));
        assertDoesNotThrow(() -> policy.requireOpen(false, null, now));
    }
}
