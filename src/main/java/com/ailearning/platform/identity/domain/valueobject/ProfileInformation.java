package com.ailearning.platform.identity.domain.valueobject;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.net.URI;
import java.util.Set;

public record ProfileInformation(String bio, String location, String website, String coverTheme) {
    public ProfileInformation {
        bio = text(bio, 400);
        location = text(location, 120);
        website = text(website, 500);
        if (!Set.of("aurora", "ocean", "sunset", "forest")
                .contains(coverTheme == null ? "" : coverTheme)) throw invalid();
        if (!website.isEmpty()) {
            try {
                URI uri = URI.create(website);
                if (uri.getHost() == null
                        || uri.getRawUserInfo() != null
                        || !("https".equalsIgnoreCase(uri.getScheme())
                                || "http".equalsIgnoreCase(uri.getScheme()))) throw invalid();
            } catch (IllegalArgumentException error) {
                throw invalid();
            }
        }
    }

    private static String text(String value, int max) {
        String result = value == null ? "" : value.strip();
        if (result.length() > max
                || result.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n'))
            throw invalid();
        return result;
    }

    private static BusinessException invalid() {
        return new BusinessException(
                "invalid_social_profile",
                ErrorType.BAD_REQUEST,
                "Thông tin hồ sơ không hợp lệ; website phải là đường dẫn HTTP(S).");
    }
}
