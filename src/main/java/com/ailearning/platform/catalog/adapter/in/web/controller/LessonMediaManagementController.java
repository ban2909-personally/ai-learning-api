package com.ailearning.platform.catalog.adapter.in.web.controller;

import com.ailearning.platform.catalog.adapter.in.web.dto.response.LessonMediaResponse;
import com.ailearning.platform.catalog.api.contract.LessonMediaUpload;
import com.ailearning.platform.catalog.api.usecase.LessonMediaManagementUseCase;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instructor/courses")
public class LessonMediaManagementController {
    private final LessonMediaManagementUseCase media;
    private final AccountAccess accounts;

    public LessonMediaManagementController(
            LessonMediaManagementUseCase media, AccountAccess accounts) {
        this.media = media;
        this.accounts = accounts;
    }

    @PutMapping(path = "/{courseSlug}/lessons/{lessonId}/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'LECTURE', 'ADMIN')")
    LessonMediaResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String courseSlug,
            @PathVariable UUID lessonId,
            @RequestPart("file") MultipartFile file) {
        var actor = UUID.fromString(jwt.getSubject());
        var roles = accounts.requireActive(actor).roles();
        if (roles.stream()
                .noneMatch(
                        role ->
                                role.equals("ADMIN")
                                        || role.equals("LECTURE")
                                        || role.equals("INSTRUCTOR"))) {
            throw new BusinessException(
                    "media_upload_denied",
                    ErrorType.FORBIDDEN,
                    "Tài khoản không có quyền đăng tải nội dung.");
        }
        try (InputStream content = file.getInputStream()) {
            var upload = new LessonMediaUpload(file.getContentType(), file.getSize(), content);
            return LessonMediaResponse.from(
                    media.upload(actor, roles.contains("ADMIN"), courseSlug, lessonId, upload));
        } catch (IOException exception) {
            var error =
                    new BusinessException(
                            "media_upload_unreadable",
                            ErrorType.BAD_REQUEST,
                            "Không thể đọc tệp nội dung đã tải lên.");
            error.initCause(exception);
            throw error;
        }
    }
}
