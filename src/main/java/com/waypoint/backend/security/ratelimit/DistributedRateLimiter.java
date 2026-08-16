package com.waypoint.backend.security.ratelimit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Service
public class DistributedRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long MAX_TRACKED_WINDOWS = 50_000L;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DistributedRateLimiter(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean allow(String key, int limit) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> allowInTransaction(key, limit)));
        } catch (DuplicateKeyException exception) {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> allowInTransaction(key, limit)));
        }
    }

    private boolean allowInTransaction(String key, int limit) {
        Instant now = Instant.now();
        RateWindow current = jdbcTemplate.query(
                "SELECT window_started_at, request_count, expires_at FROM request_rate_limit_windows WHERE rate_key = ? FOR UPDATE",
                resultSet -> resultSet.next()
                        ? new RateWindow(
                                resultSet.getTimestamp("window_started_at").toInstant(),
                                resultSet.getInt("request_count"),
                                resultSet.getTimestamp("expires_at").toInstant()
                        )
                        : null,
                key
        );

        if (current == null) {
            Long tracked = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM request_rate_limit_windows", Long.class);
            if (tracked != null && tracked >= MAX_TRACKED_WINDOWS) {
                return false;
            }
            Instant expiresAt = now.plus(WINDOW);
            jdbcTemplate.update(
                    "INSERT INTO request_rate_limit_windows(rate_key, window_started_at, request_count, expires_at) VALUES (?, ?, ?, ?)",
                    key,
                    Timestamp.from(now),
                    1,
                    Timestamp.from(expiresAt)
            );
            return true;
        }

        if (!current.expiresAt().isAfter(now)) {
            jdbcTemplate.update(
                    "UPDATE request_rate_limit_windows SET window_started_at = ?, request_count = 1, expires_at = ? WHERE rate_key = ?",
                    Timestamp.from(now),
                    Timestamp.from(now.plus(WINDOW)),
                    key
            );
            return true;
        }

        if (current.requestCount() >= limit) {
            return false;
        }

        jdbcTemplate.update(
                "UPDATE request_rate_limit_windows SET request_count = request_count + 1 WHERE rate_key = ?",
                key
        );
        return true;
    }

    @Scheduled(fixedDelayString = "${security.rate-limit-cleanup-ms:60000}")
    public void cleanupExpired() {
        jdbcTemplate.update(
                "DELETE FROM request_rate_limit_windows WHERE expires_at <= ?",
                Timestamp.from(Instant.now())
        );
    }

    private record RateWindow(Instant windowStartedAt, int requestCount, Instant expiresAt) {
    }
}
