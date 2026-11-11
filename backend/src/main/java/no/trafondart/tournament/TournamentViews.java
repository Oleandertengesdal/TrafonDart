package no.trafondart.tournament;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.trafondart.game.GameConfig;
import no.trafondart.match.MatchViews.ConfigView;
import no.trafondart.user.UserDtos.PlayerSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TournamentViews {

    private TournamentViews() {
    }

    public static final int MAX_PARTICIPANTS = 32;

    public record CreateTournamentRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Tournament.Format format,
            @NotNull Integer startScore,
            @NotNull GameConfig.InRule inRule,
            @NotNull GameConfig.OutRule outRule,
            @NotNull @Min(1) @Max(GameConfig.MAX_LEGS_TO_WIN) Integer legsToWin,
            Boolean rated,
            @NotNull @Size(max = MAX_PARTICIPANTS) List<Long> participantIds) {
    }

    public record ParticipantsRequest(@NotNull @Size(max = MAX_PARTICIPANTS) List<Long> participantIds) {
    }

    public record TournamentSummary(UUID id, String name, String format, String status, ConfigView config,
                                    boolean rated, int participantCount, PlayerSummary organiser,
                                    PlayerSummary winner, Instant createdAt, Instant startedAt, Instant completedAt) {
    }

    public record Participant(PlayerSummary player, int seed) {
    }

    public record FixtureView(Long id, int round, int position, PlayerSummary player1, PlayerSummary player2,
                              UUID matchId, String matchStatus, Integer winnerSlot, String status,
                              int[] legsWon) {
    }

    public record StandingRow(PlayerSummary player, int played, int won, int lost, int legsFor, int legsAgainst,
                              int legDiff, int points) {
    }

    public record TournamentDetail(TournamentSummary summary, Long organiserId, List<Participant> participants,
                                   List<FixtureView> fixtures, List<StandingRow> standings, int rounds) {
    }

    public record StartedFixture(UUID matchId) {
    }
}
