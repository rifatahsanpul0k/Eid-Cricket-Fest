package com.eidcricketfest.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void shouldApplyAllMigrations() {

        var current =
                flyway.info().current();

        assertThat(current)
                .isNotNull();

        assertThat(
                current.getVersion().getVersion()
        ).isEqualTo("23");
    }

    @Test
    void shouldCreateCoreTables() {

        assertTableExists("users");
        assertTableExists("player_registrations");
        assertTableExists("registration_payments");
        assertTableExists("drafts");
        assertTableExists("matches");
        assertTableExists("match_sides");
        assertTableExists("innings");
        assertTableExists("deliveries");
        assertTableExists("wickets");
        assertTableExists("tournament_player_awards");
        assertTableExists("match_operation_audits");

        assertColumnExists(
                "deliveries",
                "undo_client_event_id"
        );

        assertColumnExists(
                "innings",
                "score_revision"
        );

        assertColumnExists(
                "matches",
                "match_type"
        );

        assertColumnExists(
                "matches",
                "result_status"
        );

        assertColumnExists(
                "matches",
                "rematch_of_match_id"
        );

        assertColumnExists(
                "matches",
                "superseded_by_match_id"
        );

        assertColumnExists(
                "tournament_editions",
                "champion_tournament_team_id"
        );

        assertColumnExists(
                "tournament_editions",
                "runner_up_tournament_team_id"
        );

        assertColumnExists(
                "tournament_editions",
                "final_match_id"
        );

        assertColumnExists(
                "tournament_editions",
                "completed_at"
        );

        assertColumnExists(
                "playing_xi_entries",
                "player_id"
        );

        assertColumnExists(
                "playing_xi_entries",
                "match_side_id"
        );
    }

    private void assertTableExists(
            String table
    ) {

        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'public'
                              AND table_name = ?
                        )
                        """,
                        Boolean.class,
                        table
                );

        assertThat(exists).isTrue();
    }

    private void assertColumnExists(
            String table,
            String column
    ) {

        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = ?
                              AND column_name = ?
                        )
                        """,
                        Boolean.class,
                        table,
                        column
                );

        assertThat(exists).isTrue();
    }
}
