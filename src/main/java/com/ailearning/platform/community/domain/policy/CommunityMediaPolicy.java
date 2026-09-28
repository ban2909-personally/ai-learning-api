package com.ailearning.platform.community.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.nio.charset.StandardCharsets;

public class CommunityMediaPolicy {
    public void validate(String type, long size, byte[] header) {
        if (size <= 0 || size >= 10_000_000L) {
            throw new BusinessException(
                    "community_media_size",
                    ErrorType.PAYLOAD_TOO_LARGE,
                    "Ảnh/video phải nhỏ hơn 10 MB.");
        }
        if (header == null || header.length == 0 || header.length > size) {
            throw new BusinessException(
                    "community_media_type", ErrorType.BAD_REQUEST, "File media không hợp lệ.");
        }
        boolean matches =
                switch (type == null ? "" : type) {
                    case "image/jpeg" ->
                            header.length >= 3
                                    && (header[0] & 255) == 255
                                    && (header[1] & 255) == 216
                                    && (header[2] & 255) == 255;
                    case "image/png" ->
                            header.length >= 8
                                    && (header[0] & 255) == 137
                                    && text(header, 1, 3).equals("PNG")
                                    && header[4] == 13
                                    && header[5] == 10
                                    && header[6] == 26
                                    && header[7] == 10;
                    case "image/webp" ->
                            header.length >= 12
                                    && text(header, 0, 4).equals("RIFF")
                                    && text(header, 8, 4).equals("WEBP");
                    case "video/mp4" -> header.length >= 16 && text(header, 4, 4).equals("ftyp");
                    case "video/webm" ->
                            header.length >= 4
                                    && (header[0] & 255) == 26
                                    && (header[1] & 255) == 69
                                    && (header[2] & 255) == 223
                                    && (header[3] & 255) == 163;
                    default -> false;
                };
        if (!matches)
            throw new BusinessException(
                    "community_media_type",
                    ErrorType.BAD_REQUEST,
                    "Chỉ nhận JPEG, PNG, WebP, MP4 hoặc WebM đúng định dạng.");
    }

    private String text(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
