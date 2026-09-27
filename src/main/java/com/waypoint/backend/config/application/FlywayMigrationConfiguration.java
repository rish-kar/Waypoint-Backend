package com.waypoint.backend.config.application;

import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FlywayMigrationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationConfiguration.class);

    private static final String ONBOARDING_MIGRATION_VERSION = "30";
    private static final String APPLIED_V30_CHECKSUM = "-1709783418";
    private static final String RESOLVED_V30_CHECKSUM = "1345132987";

    @Bean
    FlywayMigrationStrategy waypointFlywayMigrationStrategy() {
        return flyway -> {
            ValidateResult validation = flyway.validateWithResult();

            if (!validation.validationSuccessful && isKnownV30ChecksumMismatchWithOnlyPendingMigrations(validation)) {
                log.warn(
                        "Repairing known Flyway V30 onboarding checksum mismatch ({} -> {}) before applying pending migrations",
                        APPLIED_V30_CHECKSUM,
                        RESOLVED_V30_CHECKSUM
                );
                flyway.repair();
            }

            flyway.migrate();
        };
    }

    static boolean isKnownV30ChecksumMismatchWithOnlyPendingMigrations(ValidateResult validation) {
        if (validation == null
                || validation.validationSuccessful
                || validation.invalidMigrations == null
                || validation.invalidMigrations.isEmpty()) {
            return false;
        }

        List<ValidateOutput> invalid = validation.invalidMigrations;

        long knownV30Mismatches = invalid.stream()
                .filter(FlywayMigrationConfiguration::isExactKnownV30Mismatch)
                .count();

        if (knownV30Mismatches != 1) {
            return false;
        }

        return invalid.stream().allMatch(entry ->
                isExactKnownV30Mismatch(entry) || isExpectedPendingMigration(entry)
        );
    }

    private static boolean isExactKnownV30Mismatch(ValidateOutput invalid) {
        if (invalid == null
                || !ONBOARDING_MIGRATION_VERSION.equals(invalid.version)
                || invalid.errorDetails == null
                || invalid.errorDetails.errorCode != CoreErrorCode.CHECKSUM_MISMATCH) {
            return false;
        }

        String message = String.valueOf(invalid.errorDetails.errorMessage);
        return message.contains("Migration checksum mismatch for migration version 30")
                && message.contains("Applied to database : " + APPLIED_V30_CHECKSUM)
                && message.contains("Resolved locally    : " + RESOLVED_V30_CHECKSUM);
    }

    private static boolean isExpectedPendingMigration(ValidateOutput invalid) {
        if (invalid == null || invalid.errorDetails == null) {
            return false;
        }
        return invalid.errorDetails.errorCode == CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED
                || invalid.errorDetails.errorCode == CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED;
    }
}
