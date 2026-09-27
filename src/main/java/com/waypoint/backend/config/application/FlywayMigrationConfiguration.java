package com.waypoint.backend.config.application;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Objects;

@Configuration
public class FlywayMigrationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationConfiguration.class);

    private static final String ONBOARDING_MIGRATION_VERSION = "30";
    private static final int APPLIED_V30_CHECKSUM = -1709783418;
    private static final int RESOLVED_V30_CHECKSUM = 1345132987;

    @Bean
    FlywayMigrationStrategy waypointFlywayMigrationStrategy() {
        return flyway -> {
            ValidateResult validation = flyway.validateWithResult();

            if (!validation.validationSuccessful && isKnownV30ChecksumMismatch(validation, flyway.info().all())) {
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

    static boolean isKnownV30ChecksumMismatch(ValidateResult validation, MigrationInfo[] migrations) {
        if (validation == null
                || validation.validationSuccessful
                || validation.invalidMigrations == null
                || validation.invalidMigrations.size() != 1) {
            return false;
        }

        ValidateOutput invalid = validation.invalidMigrations.getFirst();
        if (!ONBOARDING_MIGRATION_VERSION.equals(invalid.version)) {
            return false;
        }

        return Arrays.stream(migrations == null ? new MigrationInfo[0] : migrations)
                .filter(Objects::nonNull)
                .filter(info -> info.getVersion() != null)
                .filter(info -> ONBOARDING_MIGRATION_VERSION.equals(info.getVersion().getVersion()))
                .anyMatch(info ->
                        Objects.equals(info.getAppliedChecksum(), APPLIED_V30_CHECKSUM)
                                && Objects.equals(info.getResolvedChecksum(), RESOLVED_V30_CHECKSUM)
                                && !info.isChecksumMatching()
                );
    }
}
