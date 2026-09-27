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
    void recognizesOnlyTheKnownV30ChecksumMismatch() {
        ErrorDetails details = new ErrorDetails(
                CoreErrorCode.CHECKSUM_MISMATCH,
                "Migration checksum mismatch for migration version 30\n"
                        + "-> Applied to database : -1709783418\n"
                        + "-> Resolved locally    : 1345132987\n"
                        + "Either revert the changes to the migration, or run repair to update the schema history."
        );
        ValidateOutput invalid = new ValidateOutput("30", "add onboarding completion", "", details);
        ValidateResult validation = new ValidateResult(
                "test",
                "postgres",
                null,
                false,
                30,
                List.of(invalid),
                List.of()
        );

        assertThat(FlywayMigrationConfiguration.isKnownV30ChecksumMismatch(validation)).isTrue();
    }

    @Test
    void refusesToRepairDifferentChecksumForV30() {
        ErrorDetails details = new ErrorDetails(
                CoreErrorCode.CHECKSUM_MISMATCH,
                "Migration checksum mismatch for migration version 30\n"
                        + "-> Applied to database : 111\n"
                        + "-> Resolved locally    : 222"
        );
        ValidateOutput invalid = new ValidateOutput("30", "add onboarding completion", "", details);
        ValidateResult validation = new ValidateResult(
                "test",
                "postgres",
                null,
                false,
                30,
                List.of(invalid),
                List.of()
        );

        assertThat(FlywayMigrationConfiguration.isKnownV30ChecksumMismatch(validation)).isFalse();
    }

    @Test
    void refusesToRepairUnknownMigrationMismatch() {
        ErrorDetails details = new ErrorDetails(
                CoreErrorCode.CHECKSUM_MISMATCH,
                "Migration checksum mismatch for migration version 29"
        );
        ValidateOutput invalid = new ValidateOutput("29", "other migration", "", details);
        ValidateResult validation = new ValidateResult(
                "test",
                "postgres",
                null,
                false,
                30,
                List.of(invalid),
                List.of()
        );

        assertThat(FlywayMigrationConfiguration.isKnownV30ChecksumMismatch(validation)).isFalse();
    }
}
