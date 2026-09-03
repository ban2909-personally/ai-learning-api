package com.ailearning.platform.mentoring.adapter.out.persistence.jpa.entity;

import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mentor_messages")
public class MentorMessageJpaEntity {
    @Id
    private UUID id;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MentorMessageRole role;
    @Column(nullable = false)
    private String content;
    @Column(name = "provider_model")
    private String providerModel;
    @Column(name = "input_tokens")
    private Integer inputTokens;
    @Column(name = "output_tokens")
    private Integer outputTokens;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MentorMessageJpaEntity() {
    }

    public MentorMessageJpaEntity(
            UUID id,
            UUID conversationId,
            MentorMessageRole role,
            String content,
            String providerModel,
            Integer inputTokens,
            Integer outputTokens,
            Instant createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.providerModel = providerModel;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public MentorMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getProviderModel() {
        return providerModel;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

