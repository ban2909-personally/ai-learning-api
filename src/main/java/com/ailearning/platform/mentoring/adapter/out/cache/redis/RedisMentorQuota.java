package com.ailearning.platform.mentoring.adapter.out.cache.redis;

import com.ailearning.platform.mentoring.application.port.out.MentorQuota;
import com.ailearning.platform.mentoring.config.MentorQuotaProperties;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RedisMentorQuota implements MentorQuota {
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final MentorQuotaProperties properties;

    public RedisMentorQuota(StringRedisTemplate redis, MentorQuotaProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Decision consume(UUID userId) {
        Duration window = properties.window();
        try {
            Long count = redis.execute(
                    CONSUME,
                    List.of(properties.keyPrefix() + ":user:" + userId),
                    Long.toString(window.toMillis())
            );
            if (count == null) {
                throw new IllegalStateException("Redis quota script returned no result");
            }
            boolean allowed = count <= properties.requests();
            int remaining = (int) Math.max(0, properties.requests() - count);
            return new Decision(allowed, remaining);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "mentor_quota_unavailable",
                    ErrorType.SERVICE_UNAVAILABLE,
                    "AI Mentor đang tạm gián đoạn. Vui lòng thử lại sau."
            );
        }
    }
}
