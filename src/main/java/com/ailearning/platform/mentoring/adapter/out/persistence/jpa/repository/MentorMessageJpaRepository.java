package com.ailearning.platform.mentoring.adapter.out.persistence.jpa.repository;

import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.entity.MentorMessageJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MentorMessageJpaRepository extends JpaRepository<MentorMessageJpaEntity, UUID> {
    List<MentorMessageJpaEntity> findByConversationIdOrderByCreatedAtDescIdDesc(
            UUID conversationId,
            Pageable pageable
    );
}
