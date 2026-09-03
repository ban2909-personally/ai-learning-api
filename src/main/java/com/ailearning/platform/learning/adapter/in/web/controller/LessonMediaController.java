package com.ailearning.platform.learning.adapter.in.web.controller;

import com.ailearning.platform.catalog.api.contract.LessonMediaStream;
import com.ailearning.platform.learning.api.usecase.LessonMediaAccessUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media/courses")
public class LessonMediaController {
    private final LessonMediaAccessUseCase media;

    public LessonMediaController(LessonMediaAccessUseCase media) {
        this.media = media;
    }

    @GetMapping("/{courseSlug}/lessons/{lessonId}")
    ResponseEntity<StreamingResponseBody> stream(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseSlug,
            @PathVariable UUID lessonId,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        boolean partial = rangeHeader != null && !rangeHeader.isBlank();
        LessonMediaStream stream = media.open(
                UUID.fromString(jwt.getSubject()),
                courseSlug,
                lessonId,
                HttpMediaRangeParser.parse(rangeHeader)
        );
        StreamingResponseBody body = output -> {
            try (stream) {
                stream.content().transferTo(output);
            }
        };

        var response = ResponseEntity.status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_TYPE, stream.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(stream.length()))
                .header(HttpHeaders.ETAG, quoteEtag(stream.etag()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate());
        if (partial) {
            long end = stream.start() + stream.length() - 1;
            response.header(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes %d-%d/%d".formatted(stream.start(), end, stream.totalLength())
            );
        }
        return response.body(body);
    }

    private String quoteEtag(String etag) {
        return '"' + etag.replace("\"", "") + '"';
    }
}
