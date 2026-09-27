package com.waypoint.backend.config.application;

import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

            if (!validation.validationSuccessful && isKnownV30ChecksumMismatch(validation)) {
                log.warn(
                        "Repairing known Flyway V30 onboarding checksum mismatch ({} -> {})",
                        APPLIED_V30_CHECKSUM,
                        RESOLVED_V30_CHECKSUM
                );
                flyway.repair();
            }

            flyway.migrate();
        };
    }

    static boolean isKnownV30ChecksumMismatch(ValidateResult validation) {
        if (validation == null
                || validation.validationSuccessful
                || validation.invalidMigrations == null
                || validation.invalidMigrations.size() != 1) {
            return false;
        }

        ValidateOutput invalid = validation.invalidMigrations.getFirst();
        if (!ONBOARDING_MIGRATION_VERSION.equals(invalid.version)
                || invalid.errorDetails == null
                || invalid.errorDetails.errorCode != CoreErrorCode.CHECKSUM_MISMATCH) {
            return false;
        }

        String message = String.valueOf(invalid.errorDetails.errorMessage);
        return message.contains("Migration checksum mismatch for migration version 30")
                && message.contains("Applied to database : " + APPLIED_V30_CHECKSUM)
                && message.contains("Resolved locally    : " + RESOLVED_V30_CHECKSUM);
    }
}
