package com.ailearning.platform.community.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ailearning.platform.community.domain.model.MemberRole;
import com.ailearning.platform.community.domain.model.MemberStatus;
import com.ailearning.platform.community.domain.model.Membership;
import com.ailearning.platform.community.domain.model.Space;
import com.ailearning.platform.community.domain.model.SpaceKind;
import com.ailearning.platform.community.domain.model.SpaceVisibility;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class CommunityPolicyTest {
    private final CommunityPolicy policy = new CommunityPolicy();
    private final UUID spaceId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private Space space(SpaceKind kind, SpaceVisibility visibility) {
        return new Space(spaceId, ownerId, kind, visibility, "Java community", "", Instant.EPOCH);
    }

    private Membership member(MemberRole role, MemberStatus status) {
        return new Membership(spaceId, ownerId, role, status);
    }

    @Test
    void privateGroupsHideContentUntilMembershipIsActive() {
        Space privateGroup = space(SpaceKind.GROUP, SpaceVisibility.PRIVATE);
        assertEquals(
                ErrorType.FORBIDDEN,
                assertThrows(
                                BusinessException.class,
                                () -> policy.requireVisible(privateGroup, null))
                        .type());
        assertThrows(
                BusinessException.class,
                () ->
                        policy.requireVisible(
                                privateGroup, member(MemberRole.MEMBER, MemberStatus.PENDING)));
        assertDoesNotThrow(
                () ->
                        policy.requireVisible(
                                privateGroup, member(MemberRole.MEMBER, MemberStatus.ACTIVE)));
    }

    @Test
    void pagePublishingAndGroupParticipationHaveDifferentRules() {
        Space page = space(SpaceKind.PAGE, SpaceVisibility.PUBLIC);
        Space group = space(SpaceKind.GROUP, SpaceVisibility.PUBLIC);
        Membership regular = member(MemberRole.MEMBER, MemberStatus.ACTIVE);
        assertThrows(BusinessException.class, () -> policy.requirePosting(page, regular));
        assertDoesNotThrow(
                () -> policy.requirePosting(page, member(MemberRole.OWNER, MemberStatus.ACTIVE)));
        assertDoesNotThrow(() -> policy.requirePosting(group, regular));
        assertThrows(BusinessException.class, () -> policy.requirePosting(group, null));
        assertThrows(BusinessException.class, () -> policy.requireInteraction(group, null));
        assertDoesNotThrow(() -> policy.requireInteraction(page, null));
    }

    @Test
    void ownerCannotBeRemovedAndOnlyOwnerCanPromote() {
        Membership owner = member(MemberRole.OWNER, MemberStatus.ACTIVE);
        Membership admin = member(MemberRole.ADMIN, MemberStatus.ACTIVE);
        Membership regular = member(MemberRole.MEMBER, MemberStatus.ACTIVE);
        assertThrows(BusinessException.class, () -> policy.requireRemoval(owner, owner));
        assertThrows(BusinessException.class, () -> policy.requireRemoval(admin, admin));
        assertDoesNotThrow(() -> policy.requireRemoval(owner, regular));
        assertThrows(
                BusinessException.class,
                () -> policy.requireRoleChange(admin, regular, MemberRole.ADMIN));
        assertDoesNotThrow(() -> policy.requireRoleChange(owner, regular, MemberRole.ADMIN));
        assertThrows(
                BusinessException.class,
                () -> policy.requireRoleChange(owner, regular, MemberRole.OWNER));
    }

    @Test
    void privatePostsCannotBeSharedAndPageMustBePublic() {
        assertThrows(
                BusinessException.class,
                () -> policy.requireShareable(space(SpaceKind.GROUP, SpaceVisibility.PRIVATE)));
        assertDoesNotThrow(
                () -> policy.requireShareable(space(SpaceKind.GROUP, SpaceVisibility.PUBLIC)));
        assertThrows(
                BusinessException.class,
                () -> policy.validateSpace("Page", "", SpaceKind.PAGE, SpaceVisibility.PRIVATE));
        assertEquals("hello", policy.postBody(" hello ", false));
        assertThrows(BusinessException.class, () -> policy.commentBody(" "));
    }
}
