package com.waypoint.backend.config.application;

import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.ErrorDetails;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationConfigurationTests {

    @Test
    void recognizesKnownV30MismatchAlongsidePendingV31() {
        ValidateOutput v30 = new ValidateOutput(
                "30",
                "add onboarding completion",
                "",
                new ErrorDetails(
                        CoreErrorCode.CHECKSUM_MISMATCH,
                        "Migration checksum mismatch for migration version 30\n"
                                + "-> Applied to database : -1709783418\n"
                                + "-> Resolved locally    : 1345132987\n"
                                + "Either revert the changes to the migration, or run repair to update the schema history."
                )
        );
        ValidateOutput v31 = new ValidateOutput(
                "31",
                "restore usd plan catalogue",
                "",
                new ErrorDetails(
                        CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED,
                        "Detected resolved migration not applied to database: 31"
                )
        );
        ValidateResult validation = validation(v30, v31);

        assertThat(FlywayMigrationConfiguration
                .isKnownV30ChecksumMismatchWithOnlyPendingMigrations(validation))
                .isTrue();
    }

    @Test
    void recognizesKnownV30MismatchWhenItIsTheOnlyValidationProblem() {
        ValidateOutput v30 = new ValidateOutput(
                "30",
                "add onboarding completion",
                "",
                new ErrorDetails(
                        CoreErrorCode.CHECKSUM_MISMATCH,
                        "Migration checksum mismatch for migration version 30\n"
                                + "-> Applied to database : -1709783418\n"
                                + "-> Resolved locally    : 1345132987"
                )
        );

        assertThat(FlywayMigrationConfiguration
                .isKnownV30ChecksumMismatchWithOnlyPendingMigrations(validation(v30)))
                .isTrue();
    }

    @Test
    void refusesDifferentChecksumForV30() {
        ValidateOutput v30 = new ValidateOutput(
                "30",
                "add onboarding completion",
                "",
                new ErrorDetails(
                        CoreErrorCode.CHECKSUM_MISMATCH,
                        "Migration checksum mismatch for migration version 30\n"
                                + "-> Applied to database : 111\n"
                                + "-> Resolved locally    : 222"
                )
        );

        assertThat(FlywayMigrationConfiguration
                .isKnownV30ChecksumMismatchWithOnlyPendingMigrations(validation(v30)))
                .isFalse();
    }

    @Test
    void refusesRepairWhenAnotherRealValidationErrorExists() {
        ValidateOutput v30 = new ValidateOutput(
                "30",
                "add onboarding completion",
                "",
                new ErrorDetails(
                        CoreErrorCode.CHECKSUM_MISMATCH,
                        "Migration checksum mismatch for migration version 30\n"
                                + "-> Applied to database : -1709783418\n"
                                + "-> Resolved locally    : 1345132987"
                )
        );
        ValidateOutput v29 = new ValidateOutput(
                "29",
                "other migration",
                "",
                new ErrorDetails(
                        CoreErrorCode.CHECKSUM_MISMATCH,
                        "Migration checksum mismatch for migration version 29"
                )
        );

        assertThat(FlywayMigrationConfiguration
                .isKnownV30ChecksumMismatchWithOnlyPendingMigrations(validation(v30, v29)))
                .isFalse();
    }

    private static ValidateResult validation(ValidateOutput... invalid) {
        return new ValidateResult(
                "test",
                "postgres",
                null,
                false,
                31,
                List.of(invalid),
                List.of()
        );
    }
}
