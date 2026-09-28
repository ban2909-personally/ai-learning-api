package com.ailearning.platform.community.domain.policy;

import com.ailearning.platform.community.domain.model.PollDefinition;
import com.ailearning.platform.community.domain.model.PostFeatures;
import com.ailearning.platform.community.domain.valueobject.PostAppearance;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class PostFeaturesPolicy {
    public PostFeatures normalize(PostFeatures input, Instant now) {
        if (input == null) return null;
        PostAppearance appearance = appearance(input.appearance());
        PollDefinition poll = input.poll();
        if (poll != null) {
            if (poll.kind() == null
                    || poll.question() == null
                    || poll.question().isBlank()
                    || poll.question().length() > 300
                    || poll.options() == null
                    || poll.options().size() < 2
                    || poll.options().size() > 8)
                throw invalid("Bình chọn cần câu hỏi và 2–8 lựa chọn.");
            List<String> labels =
                    poll.options().stream()
                            .map(
                                    label -> {
                                        if (label == null
                                                || label.isBlank()
                                                || label.length() > 160)
                                            throw invalid("Lựa chọn cần 1–160 ký tự.");
                                        return label.trim();
                                    })
                            .toList();
            if (labels.stream().map(label -> label.toLowerCase(Locale.ROOT)).distinct().count()
                    != labels.size()) throw invalid("Lựa chọn không được trùng.");
            if (poll.closesAt() != null
                    && (!poll.closesAt().isAfter(now)
                            || poll.closesAt().isAfter(now.plus(Duration.ofDays(30)))))
                throw invalid("Hạn bình chọn phải trong 30 ngày tới.");
            poll = new PollDefinition(poll.kind(), poll.question().trim(), labels, poll.closesAt());
        }
        return new PostFeatures(appearance, poll);
    }

    private PostAppearance appearance(PostAppearance input) {
        if (input == null) return null;
        String link = input.attachmentUrl();
        if (link != null && !link.isBlank()) {
            try {
                link = link.trim();
                URI uri = URI.create(link);
                if (link.length() > 2048
                        || uri.getHost() == null
                        || uri.getUserInfo() != null
                        || (!"https".equalsIgnoreCase(uri.getScheme())
                                && !"http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getPort() < -1
                        || uri.getPort() > 65535) throw new IllegalArgumentException();
                link = uri.toASCIIString();
                if (link.length() > 2048) throw new IllegalArgumentException();
            } catch (IllegalArgumentException failure) {
                throw invalid("Link đính kèm phải là HTTP(S), không chứa thông tin đăng nhập.");
            }
        } else link = null;
        String background = input.backgroundColor(), font = input.fontColor();
        if (background != null || font != null) {
            if (background == null
                    || font == null
                    || !background.matches("#[0-9a-fA-F]{6}")
                    || !font.matches("#[0-9a-fA-F]{6}")) throw invalid("Màu phải có dạng #RRGGBB.");
            double first = luminance(background), second = luminance(font);
            if ((Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05) < 4.5)
                throw invalid("Màu chữ và nền cần tương phản ít nhất 4.5:1.");
        }
        return new PostAppearance(link, background, font);
    }

    private double luminance(String hex) {
        double sum = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int index = 0; index < 3; index++) {
            double channel =
                    Integer.parseInt(hex.substring(1 + index * 2, 3 + index * 2), 16) / 255.0;
            sum +=
                    weights[index]
                            * (channel <= 0.04045
                                    ? channel / 12.92
                                    : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return sum;
    }

    public void requireOpen(boolean closed, Instant closesAt, Instant now) {
        if (closed || (closesAt != null && !closesAt.isAfter(now)))
            throw new BusinessException("poll_closed", ErrorType.CONFLICT, "Bình chọn đã đóng.");
    }

    private BusinessException invalid(String detail) {
        return new BusinessException("invalid_post_features", ErrorType.BAD_REQUEST, detail);
    }
}
