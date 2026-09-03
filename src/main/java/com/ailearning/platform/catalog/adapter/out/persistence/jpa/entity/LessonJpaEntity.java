package com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "lessons")
public class LessonJpaEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSectionJpaEntity section;
    @Column(nullable = false, length = 180) private String title;
    @Column(name = "content_url", nullable = false, length = 1000) private String contentUrl;
    @Column(name = "media_object_key", length = 700) private String mediaObjectKey;
    @Column(name = "media_content_type", length = 100) private String mediaContentType;
    @Column(name = "media_size_bytes") private Long mediaSizeBytes;
    @Column(name = "media_etag", length = 128) private String mediaEtag;
    @Column(name = "duration_seconds", nullable = false) private int durationSeconds;
    @Column(nullable = false) private boolean preview;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    protected LessonJpaEntity() {}
    public CourseSectionJpaEntity getSection() { return section; }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getContentUrl() { return contentUrl; }
    public String getMediaObjectKey() { return mediaObjectKey; }
    public String getMediaContentType() { return mediaContentType; }
    public Long getMediaSizeBytes() { return mediaSizeBytes; }
    public String getMediaEtag() { return mediaEtag; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isPreview() { return preview; }
    public int getDisplayOrder() { return displayOrder; }
    public void attachMedia(String objectKey, String contentType, long sizeBytes, String etag, String url) {
        this.mediaObjectKey = objectKey;
        this.mediaContentType = contentType;
        this.mediaSizeBytes = sizeBytes;
        this.mediaEtag = etag;
        this.contentUrl = url;
    }
}
