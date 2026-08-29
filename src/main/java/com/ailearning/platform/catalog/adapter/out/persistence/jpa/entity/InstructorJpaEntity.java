package com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity(name = "CatalogInstructorJpaEntity")
@Table(name = "users")
public class InstructorJpaEntity {
    @Id private UUID id;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    protected InstructorJpaEntity() {}
    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
}
