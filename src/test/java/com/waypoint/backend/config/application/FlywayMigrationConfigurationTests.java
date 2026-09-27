package com.waypoint.backend.config.application;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlywayMigrationConfigurationTests {

    @Test
    void recognizesOnlyTheKnownV30ChecksumMismatch() {
        ValidateOutput invalid = new ValidateOutput("30", "add onboarding completion", "", null);
        ValidateResult validation = new ValidateResult(
                "test",
                "postgres",
                null,
                false,
                30,
                List.of(invalid),
                List.of()
        );

        MigrationInfo migration = mock(MigrationInfo.class);
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("30"));
        when(migration.getAppliedChecksum()).thenReturn(-1709783418);
        when(migration.getResolvedChecksum()).thenReturn(1345132987);
        when(migration.isChecksumMatching()).thenReturn(false);

        assertThat(FlywayMigrationConfiguration.isKnownV30ChecksumMismatch(
                validation,
                new MigrationInfo[]{ migration }
        )).isTrue();
    }

    @Test
    void refusesToRepairUnknownMigrationMismatch() {
        ValidateOutput invalid = new ValidateOutput("29", "other migration", "", null);
        ValidateResult validation = new ValidateResult(
                "test",
                "postgres",
                null,
                false,
                30,
                List.of(invalid),
                List.of()
        );

        assertThat(FlywayMigrationConfiguration.isKnownV30ChecksumMismatch(
                validation,
                new MigrationInfo[0]
        )).isFalse();
    }
}
