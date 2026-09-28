package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.api.contract.CommunityMediaUpload;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.api.usecase.CommunityMediaUseCase;
import com.ailearning.platform.community.domain.model.MediaByteRange;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class CommunityMediaController {
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private final CommunityMediaUseCase media;

    public CommunityMediaController(CommunityMediaUseCase media) {
        this.media = media;
    }

    @PostMapping(
            value = "/api/v1/community/posts/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PostView upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "") String body,
            @RequestParam(required = false) UUID spaceId)
            throws IOException {
        try (var input = file.getInputStream()) {
            return media.publish(
                    UUID.fromString(jwt.getSubject()),
                    spaceId,
                    body,
                    new CommunityMediaUpload(file.getContentType(), file.getSize(), input));
        }
    }

    @RequestMapping(
            value = "/api/v1/media/community/posts/{postId}",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    ResponseEntity<StreamingResponseBody> read(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        boolean head = "HEAD".equals(request.getMethod());
        MediaByteRange range = parseRange(rangeHeader);
        var content =
                media.read(
                        jwt == null ? null : UUID.fromString(jwt.getSubject()),
                        postId,
                        range,
                        head);
        var response =
                ResponseEntity.status(range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, content.contentType())
                        .header(HttpHeaders.CONTENT_LENGTH, Long.toString(content.length()))
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.ETAG, '"' + content.etag().replace("\"", "") + '"')
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .header("X-Content-Type-Options", "nosniff");
        if (range != null)
            response.header(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes %d-%d/%d"
                            .formatted(
                                    content.start(),
                                    content.start() + content.length() - 1,
                                    content.totalSize()));
        if (head) return response.build();
        return response.body(
                output -> {
                    try (content) {
                        content.content().transferTo(output);
                    }
                });
    }

    private MediaByteRange parseRange(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            if (header.length() > 100) throw new IllegalArgumentException();
            var match = RANGE.matcher(header.trim());
            if (!match.matches() || (match.group(1).isEmpty() && match.group(2).isEmpty()))
                throw new IllegalArgumentException();
            if (match.group(1).isEmpty())
                return new MediaByteRange(null, null, Long.parseLong(match.group(2)));
            return new MediaByteRange(
                    Long.parseLong(match.group(1)),
                    match.group(2).isEmpty() ? null : Long.parseLong(match.group(2)),
                    null);
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(
                    "invalid_media_range",
                    ErrorType.RANGE_NOT_SATISFIABLE,
                    "Chỉ hỗ trợ một vùng bytes hợp lệ.");
        }
    }
}
