package com.waypoint.backend.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class DistributedRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "waypoint:rate:";
    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final boolean distributedStateEnabled;

    public DistributedRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${security.distributed-state-enabled:false}") boolean distributedStateEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.distributedStateEnabled = distributedStateEnabled;
    }

    public boolean allow(String key, int limit) {
        if (!distributedStateEnabled) {
            return true;
        }
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_WINDOW,
                    List.of(KEY_PREFIX + key),
                    Long.toString(WINDOW.toMillis())
            );
            return count != null && count <= limit;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
