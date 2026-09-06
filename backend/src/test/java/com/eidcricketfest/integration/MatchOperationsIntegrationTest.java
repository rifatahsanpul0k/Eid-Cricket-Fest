package com.eidcricketfest.integration;

import com.eidcricketfest.standings.dto.StandingsResponse;
import com.eidcricketfest.standings.service.StandingsService;
import com.eidcricketfest.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MatchOperationsIntegrationTest
        extends AuthTestSupport {

    @Autowired
    private StandingsService standingsService;

    @Autowired
    private StatisticsService statisticsService;

    @Test
    void organizerCanRescheduleMatchAndReadAuditHistory()
            throws Exception {

        TestTokens organizer = organizer();
        Long matchId = createTournamentMatch("SCHEDULED");
        Long venueId = venue("Operations Reschedule");

        mockMvc.perform(
                        patch(
                                "/api/v1/matches/{matchId}/operations/reschedule",
                                matchId
                        )
                                .header("Authorization", bearer(organizer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "scheduledAt",
                                                "2026-12-16T04:00:00Z",
                                                "venueId",
                                                venueId,
                                                "oversPerInnings",
                                                8,
                                                "reason",
                                                "Rain forecast moved the fixture"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.oversPerInnings").value(8))
                .andExpect(jsonPath("$.operationHistory[0].operationType")
                        .value("RESCHEDULE"))
                .andExpect(jsonPath("$.operationHistory[0].reason")
                        .value("Rain forecast moved the fixture"));

        assertThat(auditCount(matchId, "RESCHEDULE"))
                .isEqualTo(1L);
    }

    @Test
    void playerCannotUseMatchOperations()
            throws Exception {

        TestTokens player = registerPlayer();
        Long matchId = createTournamentMatch("SCHEDULED");

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/postpone",
                                matchId
                        )
                                .header("Authorization", bearer(player))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Player should not manage fixtures"
                                        )
                                ))
                )
                .andExpect(status().isForbidden());

        assertThat(auditCount(matchId, "POSTPONE"))
                .isZero();
    }

    @Test
    void resetTossDeletesPersistedTossAndReturnsReady()
            throws Exception {

        TestTokens admin = admin();
        Long matchId = createTournamentMatch("TOSS_COMPLETED");
        Long winnerSideId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT team_a_side_id
                        FROM matches
                        WHERE id = ?
                        """,
                        Long.class,
                        matchId
                );

        jdbcTemplate.update(
                """
                INSERT INTO match_tosses (
                    match_id,
                    winner_match_side_id,
                    decision
                )
                VALUES (?, ?, 'BAT')
                """,
                matchId,
                winnerSideId
        );

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/reset-toss",
                                matchId
                        )
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Wrong toss winner recorded"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.tossCompleted").value(false));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM match_tosses
                        WHERE match_id = ?
                        """,
                        Long.class,
                        matchId
                )
        ).isZero();
    }

    @Test
    void resetTossRejectsAfterInningsStarted()
            throws Exception {

        TestTokens admin = admin();
        Long matchId = createTournamentMatch("TOSS_COMPLETED");
        MatchFixture fixture = matchFixture(matchId);

        jdbcTemplate.update(
                """
                INSERT INTO match_tosses (
                    match_id,
                    winner_match_side_id,
                    decision
                )
                VALUES (?, ?, 'BAT')
                """,
                matchId,
                fixture.teamASideId()
        );

        insertInnings(
                fixture,
                0,
                0,
                0,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/reset-toss",
                                matchId
                        )
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Toss should stay locked"
                                        )
                                ))
                )
                .andExpect(status().isConflict());

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM matches
                        WHERE id = ?
                        """,
                        String.class,
                        matchId
                )
        ).isEqualTo("TOSS_COMPLETED");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM match_tosses
                        WHERE match_id = ?
                        """,
                        Long.class,
                        matchId
                )
        ).isEqualTo(1L);
    }

    @Test
    void suspendAndResumeRestoresPriorStatus()
            throws Exception {

        TestTokens organizer = organizer();
        Long matchId = createTournamentMatch("LIVE");

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/suspend",
                                matchId
                        )
                                .header("Authorization", bearer(organizer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of("reason", "Bad light")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/resume",
                                matchId
                        )
                                .header("Authorization", bearer(organizer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of("reason", "Light improved")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));
    }

    @Test
    void suspendAndResumePreservesPersistedCricketState()
            throws Exception {

        TestTokens organizer = organizer();
        Long matchId = createTournamentMatch("LIVE");
        MatchFixture fixture = matchFixture(matchId);
        Long strikerXi = playingXi(fixture, fixture.teamAId(), fixture.teamASideId(), "Striker");
        Long nonStrikerXi = playingXi(fixture, fixture.teamAId(), fixture.teamASideId(), "Non Striker");
        Long bowlerXi = playingXi(fixture, fixture.teamBId(), fixture.teamBSideId(), "Bowler");
        Long inningsId =
                insertInnings(
                        fixture,
                        73,
                        3,
                        44,
                        120,
                        strikerXi,
                        nonStrikerXi,
                        bowlerXi
                );

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/suspend",
                                matchId
                        )
                                .header("Authorization", bearer(organizer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of("reason", "Lightning")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/resume",
                                matchId
                        )
                                .header("Authorization", bearer(organizer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of("reason", "Safe to continue")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));

        Map<String, Object> innings =
                jdbcTemplate.queryForMap(
                        """
                        SELECT total_runs,
                               wickets,
                               legal_balls,
                               target_runs,
                               current_striker_xi_id,
                               current_non_striker_xi_id,
                               current_bowler_xi_id,
                               score_revision
                        FROM innings
                        WHERE id = ?
                        """,
                        inningsId
                );

        assertThat(((Number) innings.get("total_runs")).intValue())
                .isEqualTo(73);
        assertThat(((Number) innings.get("wickets")).intValue())
                .isEqualTo(3);
        assertThat(((Number) innings.get("legal_balls")).intValue())
                .isEqualTo(44);
        assertThat(((Number) innings.get("target_runs")).intValue())
                .isEqualTo(120);
        assertThat(((Number) innings.get("current_striker_xi_id")).longValue())
                .isEqualTo(strikerXi);
        assertThat(((Number) innings.get("current_non_striker_xi_id")).longValue())
                .isEqualTo(nonStrikerXi);
        assertThat(((Number) innings.get("current_bowler_xi_id")).longValue())
                .isEqualTo(bowlerXi);
        assertThat(((Number) innings.get("score_revision")).longValue())
                .isEqualTo(9L);
    }

    @Test
    void resultReviewRestoreAndVoidControlStandingsAndStatistics()
            throws Exception {

        TestTokens admin = admin();
        Long matchId = createTournamentMatch("COMPLETED");
        MatchFixture fixture = matchFixture(matchId);
        Long strikerXi = playingXi(fixture, fixture.teamAId(), fixture.teamASideId(), "Review Batter");
        Long nonStrikerXi = playingXi(fixture, fixture.teamAId(), fixture.teamASideId(), "Review Partner");
        Long bowlerXi = playingXi(fixture, fixture.teamBId(), fixture.teamBSideId(), "Review Bowler");
        Long inningsId =
                insertInnings(
                        fixture,
                        4,
                        0,
                        1,
                        null,
                        strikerXi,
                        nonStrikerXi,
                        bowlerXi
                );
        insertDelivery(
                fixture,
                inningsId,
                strikerXi,
                nonStrikerXi,
                bowlerXi,
                userId(admin.email())
        );

        assertThat(standingFor(fixture.editionId(), fixture.teamAId()).played())
                .isEqualTo(1);
        assertThat(statisticsService.statistics(fixture.editionId()).batting())
                .isNotEmpty();

        performReasonOperation(
                admin,
                matchId,
                "review",
                "Scoring dispute"
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("UNDER_REVIEW"));

        assertThat(standingFor(fixture.editionId(), fixture.teamAId()).played())
                .isZero();
        assertThat(statisticsService.statistics(fixture.editionId()).batting())
                .isEmpty();

        performReasonOperation(
                admin,
                matchId,
                "restore-result",
                "Dispute resolved"
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("OFFICIAL"));

        assertThat(standingFor(fixture.editionId(), fixture.teamAId()).played())
                .isEqualTo(1);
        assertThat(statisticsService.statistics(fixture.editionId()).batting())
                .isNotEmpty();

        performReasonOperation(
                admin,
                matchId,
                "void-result",
                "Scorecard invalid"
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("VOID"));

        assertThat(standingFor(fixture.editionId(), fixture.teamAId()).played())
                .isZero();
        assertThat(statisticsService.statistics(fixture.editionId()).batting())
                .isEmpty();
    }

    @Test
    void rematchSupersedesOriginalAndCreatesFreshLinkedMatch()
            throws Exception {

        TestTokens admin = admin();
        Long originalMatchId = createTournamentMatch("COMPLETED");

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/rematch",
                                originalMatchId
                        )
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Result voided by committee",
                                                "scheduledAt",
                                                "2026-12-20T04:00:00Z"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.rematchOfMatchId")
                        .value(originalMatchId));

        Map<String, Object> original =
                jdbcTemplate.queryForMap(
                        """
                        SELECT result_status,
                               superseded_by_match_id
                        FROM matches
                        WHERE id = ?
                        """,
                        originalMatchId
                );

        assertThat(original.get("result_status"))
                .isEqualTo("SUPERSEDED");

        assertThat(original.get("superseded_by_match_id"))
                .isNotNull();

        Long rematchId =
                ((Number) original.get("superseded_by_match_id"))
                        .longValue();

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM innings
                        WHERE match_id = ?
                        """,
                        Long.class,
                        rematchId
                )
        ).isZero();

        assertThat(auditCount(originalMatchId, "ORDER_REMATCH"))
                .isEqualTo(1L);
    }

    @Test
    void completedFinalCanBeSupersededByFreshFinalRematch()
            throws Exception {

        TestTokens admin = admin();
        Long originalFinalId = createTournamentMatch("COMPLETED", "FINAL");

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/rematch",
                                originalFinalId
                        )
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Official final under review",
                                                "scheduledAt",
                                                "2026-12-20T04:00:00Z"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FINAL"))
                .andExpect(jsonPath("$.rematchOfMatchId")
                        .value(originalFinalId));

        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT result_status FROM matches WHERE id = ?",
                        String.class,
                        originalFinalId
                )
        ).isEqualTo("SUPERSEDED");

        assertThat(auditCount(originalFinalId, "ORDER_REMATCH"))
                .isEqualTo(1L);
    }

    @Test
    void rematchClearsUnstartedKnockoutDependentButRejectsStartedDependent()
            throws Exception {

        TestTokens admin = admin();
        Long semifinalId = createTournamentMatch("COMPLETED", "SEMI_FINAL");
        MatchFixture semifinal = matchFixture(semifinalId);
        Long otherSemifinalId = createTournamentMatchForEdition(
                semifinal.editionId(),
                2,
                "COMPLETED",
                "SEMI_FINAL"
        );
        MatchFixture otherSemifinal = matchFixture(otherSemifinalId);
        Long finalId =
                createDependentFinal(
                        semifinal,
                        otherSemifinal,
                        "SCHEDULED"
                );

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/rematch",
                                semifinalId
                        )
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Semi-final dispute"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rematchOfMatchId")
                        .value(semifinalId));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM matches
                        WHERE id = ?
                        """,
                        Long.class,
                        finalId
                )
        ).isZero();

        Long startedSemifinalId = createTournamentMatch("COMPLETED", "SEMI_FINAL");
        MatchFixture startedSemifinal = matchFixture(startedSemifinalId);
        Long otherStartedSemifinalId = createTournamentMatchForEdition(
                startedSemifinal.editionId(),
                2,
                "COMPLETED",
                "SEMI_FINAL"
        );
        MatchFixture otherStarted = matchFixture(otherStartedSemifinalId);

        createDependentFinal(
                startedSemifinal,
                otherStarted,
                "LIVE"
        );

        mockMvc.perform(
                        post(
                                "/api/v1/matches/{matchId}/operations/rematch",
                                startedSemifinalId
                        )
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        Map.of(
                                                "reason",
                                                "Should be blocked"
                                        )
                                ))
                )
                .andExpect(status().isConflict());
    }

    private TestTokens organizer()
            throws Exception {
        TestTokens tokens = registerPlayer();
        addRole(tokens.email(), "ORGANIZER");
        return login(tokens.email(), tokens.password());
    }

    private TestTokens admin()
            throws Exception {
        TestTokens tokens = registerPlayer();
        addRole(tokens.email(), "ADMIN");
        return login(tokens.email(), tokens.password());
    }

    private Long createTournamentMatch(String status) {
        return createTournamentMatch(status, "LEAGUE");
    }

    private Long createTournamentMatch(String status, String stage) {
        String suffix = UUID.randomUUID().toString();
        Long tournamentId =
                insertAndReturnId(
                        """
                        INSERT INTO tournaments (name)
                        VALUES (?)
                        """,
                        "Operations Cup " + suffix
                );

        Long editionId =
                insertAndReturnId(
                        """
                        INSERT INTO tournament_editions (
                            tournament_id,
                            name,
                            overs_per_innings,
                            playing_xi_size
                        )
                        VALUES (?, ?, 5, 2)
                        """,
                        tournamentId,
                        "Edition " + suffix
                );

        return createTournamentMatchForEdition(
                editionId,
                1,
                status,
                stage
        );
    }

    private Long createTournamentMatchForEdition(
            Long editionId,
            int matchNumber,
            String status,
            String stage
    ) {
        String suffix = UUID.randomUUID().toString();
        Long venueId = venue("Operations Ground " + suffix);
        Long teamA = tournamentTeam(editionId, "Ops Alpha " + suffix);
        Long teamB = tournamentTeam(editionId, "Ops Beta " + suffix);

        Long matchId =
                insertAndReturnId(
                        """
                        INSERT INTO matches (
                            tournament_edition_id,
                            team_a_id,
                            team_b_id,
                            venue_id,
                            stage,
                            round_number,
                            match_number,
                            scheduled_at,
                            overs_per_innings,
                            status,
                            result_status,
                            result_type
                        )
                        VALUES (?, ?, ?, ?, ?, 1, ?, ?, 5, ?, ?, ?)
                        """,
                        editionId,
                        teamA,
                        teamB,
                        venueId,
                        stage,
                        matchNumber,
                        Instant.parse("2026-12-15T04:00:00Z"),
                        status,
                        "COMPLETED".equals(status) ? "OFFICIAL" : null,
                        "COMPLETED".equals(status) ? "TIE" : null
                );

        Long sideA =
                matchSide(
                        matchId,
                        "A",
                        "Ops Alpha",
                        teamA
                );

        Long sideB =
                matchSide(
                        matchId,
                        "B",
                        "Ops Beta",
                        teamB
                );

        jdbcTemplate.update(
                """
                UPDATE matches
                SET team_a_side_id = ?,
                    team_b_side_id = ?
                WHERE id = ?
                """,
                sideA,
                sideB,
                matchId
        );

        return matchId;
    }

    private MatchFixture matchFixture(Long matchId) {
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        """
                        SELECT tournament_edition_id,
                               team_a_id,
                               team_b_id,
                               team_a_side_id,
                               team_b_side_id
                        FROM matches
                        WHERE id = ?
                        """,
                        matchId
                );

        return new MatchFixture(
                matchId,
                ((Number) row.get("tournament_edition_id")).longValue(),
                ((Number) row.get("team_a_id")).longValue(),
                ((Number) row.get("team_b_id")).longValue(),
                ((Number) row.get("team_a_side_id")).longValue(),
                ((Number) row.get("team_b_side_id")).longValue()
        );
    }

    private Long insertInnings(
            MatchFixture fixture,
            int runs,
            int wickets,
            int legalBalls,
            Integer target,
            Long strikerXi,
            Long nonStrikerXi,
            Long bowlerXi
    ) {
        return insertAndReturnId(
                """
                INSERT INTO innings (
                    match_id,
                    tournament_edition_id,
                    innings_number,
                    batting_team_id,
                    bowling_team_id,
                    batting_match_side_id,
                    bowling_match_side_id,
                    target_runs,
                    status,
                    total_runs,
                    wickets,
                    legal_balls,
                    current_striker_xi_id,
                    current_non_striker_xi_id,
                    current_bowler_xi_id,
                    score_revision
                )
                VALUES (
                    ?, ?, 1, ?, ?, ?, ?, ?, 'IN_PROGRESS',
                    ?, ?, ?, ?, ?, ?, 9
                )
                """,
                fixture.matchId(),
                fixture.editionId(),
                fixture.teamAId(),
                fixture.teamBId(),
                fixture.teamASideId(),
                fixture.teamBSideId(),
                target,
                runs,
                wickets,
                legalBalls,
                strikerXi,
                nonStrikerXi,
                bowlerXi
        );
    }

    private Long playingXi(
            MatchFixture fixture,
            Long tournamentTeamId,
            Long matchSideId,
            String name
    ) {
        String suffix = UUID.randomUUID().toString();
        Long playerId =
                insertAndReturnId(
                        """
                        INSERT INTO players (
                            full_name,
                            primary_category_id
                        )
                        SELECT ?, id
                        FROM player_categories
                        WHERE code = 'BATSMAN'
                        """,
                        name + " " + suffix
                );

        Long registrationId =
                insertAndReturnId(
                        """
                        INSERT INTO player_registrations (
                            tournament_edition_id,
                            player_id,
                            category_id,
                            status
                        )
                        SELECT ?, ?, id, 'APPROVED'
                        FROM player_categories
                        WHERE code = 'BATSMAN'
                        """,
                        fixture.editionId(),
                        playerId
                );

        return insertAndReturnId(
                """
                INSERT INTO playing_xi_entries (
                    match_id,
                    tournament_edition_id,
                    tournament_team_id,
                    match_side_id,
                    player_registration_id,
                    player_id,
                    is_captain,
                    is_wicketkeeper
                )
                VALUES (?, ?, ?, ?, ?, ?, false, false)
                """,
                fixture.matchId(),
                fixture.editionId(),
                tournamentTeamId,
                matchSideId,
                registrationId,
                playerId
        );
    }

    private void insertDelivery(
            MatchFixture fixture,
            Long inningsId,
            Long strikerXi,
            Long nonStrikerXi,
            Long bowlerXi,
            Long actorUserId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    innings_id,
                    match_id,
                    sequence_no,
                    striker_xi_id,
                    non_striker_xi_id,
                    bowler_xi_id,
                    runs_off_bat,
                    created_by_user_id
                )
                VALUES (?, ?, 1, ?, ?, ?, 4, ?)
                """,
                inningsId,
                fixture.matchId(),
                strikerXi,
                nonStrikerXi,
                bowlerXi,
                actorUserId
        );
    }

    private Long createDependentFinal(
            MatchFixture sourceA,
            MatchFixture sourceB,
            String status
    ) {
        Long matchId =
                insertAndReturnId(
                        """
                        INSERT INTO matches (
                            tournament_edition_id,
                            team_a_id,
                            team_b_id,
                            stage,
                            round_number,
                            match_number,
                            overs_per_innings,
                            status,
                            source_match_a_id,
                            source_match_b_id
                        )
                        VALUES (?, ?, ?, 'FINAL', 1, 99, 5, ?, ?, ?)
                        """,
                        sourceA.editionId(),
                        sourceA.teamAId(),
                        sourceB.teamAId(),
                        status,
                        sourceA.matchId(),
                        sourceB.matchId()
                );

        Long sideA =
                matchSide(
                        matchId,
                        "A",
                        "Final A",
                        sourceA.teamAId()
                );

        Long sideB =
                matchSide(
                        matchId,
                        "B",
                        "Final B",
                        sourceB.teamAId()
                );

        jdbcTemplate.update(
                """
                UPDATE matches
                SET team_a_side_id = ?,
                    team_b_side_id = ?
                WHERE id = ?
                """,
                sideA,
                sideB,
                matchId
        );

        return matchId;
    }

    private ResultActions performReasonOperation(
            TestTokens tokens,
            Long matchId,
            String operation,
            String reason
    ) throws Exception {
        return mockMvc.perform(
                post(
                        "/api/v1/matches/{matchId}/operations/{operation}",
                        matchId,
                        operation
                )
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                Map.of("reason", reason)
                        ))
        );
    }

    private StandingsResponse.Row standingFor(
            Long editionId,
            Long tournamentTeamId
    ) {
        return standingsService
                .getStandings(editionId)
                .standings()
                .stream()
                .filter(row ->
                        row.tournamentTeamId()
                                .equals(tournamentTeamId)
                )
                .findFirst()
                .orElseThrow();
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                Long.class,
                email
        );
    }

    private Long tournamentTeam(
            Long editionId,
            String teamName
    ) {
        Long teamId =
                insertAndReturnId(
                        """
                        INSERT INTO teams (name, short_name)
                        VALUES (?, ?)
                        """,
                        teamName,
                        teamName.substring(0, Math.min(10, teamName.length()))
                );

        return insertAndReturnId(
                """
                INSERT INTO tournament_teams (
                    tournament_edition_id,
                    team_id
                )
                VALUES (?, ?)
                """,
                editionId,
                teamId
        );
    }

    private Long matchSide(
            Long matchId,
            String sideKey,
            String displayName,
            Long tournamentTeamId
    ) {
        return insertAndReturnId(
                """
                INSERT INTO match_sides (
                    match_id,
                    side_key,
                    display_name,
                    tournament_team_id
                )
                VALUES (?, ?, ?, ?)
                """,
                matchId,
                sideKey,
                displayName,
                tournamentTeamId
        );
    }

    private Long venue(String name) {
        return insertAndReturnId(
                """
                INSERT INTO venues (name)
                VALUES (?)
                """,
                name
        );
    }

    private Long auditCount(
            Long matchId,
            String operationType
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM match_operation_audits
                WHERE match_id = ?
                  AND operation_type = ?
                """,
                Long.class,
                matchId,
                operationType
        );
    }

    private Long insertAndReturnId(
            String sql,
            Object... args
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            new String[] { "id" }
                    );

            for (int i = 0; i < args.length; i++) {
                Object value = args[i];

                if (value instanceof Instant instant) {
                    value = Timestamp.from(instant);
                }

                statement.setObject(i + 1, value);
            }

            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Insert did not return a generated key"
            );
        }

        return key.longValue();
    }

    private String bearer(TestTokens tokens) {
        return "Bearer " + tokens.accessToken();
    }

    private record MatchFixture(
            Long matchId,
            Long editionId,
            Long teamAId,
            Long teamBId,
            Long teamASideId,
            Long teamBSideId
    ) {}
}
