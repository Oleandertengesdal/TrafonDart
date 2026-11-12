package no.trafondart.party;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.trafondart.user.UserDtos.PlayerSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for /api/games. */
public final class GameViews {

    private GameViews() {
    }

    /**
     * @param playerIds for individual games, the throwing order. For doubles, the two teams one after the other:
     *                  {@code [a1, a2, b1, b2]}; the server turns that into the order A1, B1, A2, B2.
     * @param teams     doubles (X01 and Cricket only, exactly four players)
     * @param settings  optional; defaults are filled in. Killer numbers are always drawn by the server.
     */
    public record CreateGameRequest(@NotNull GameType type, @NotNull @Size(min = 1, max = 8) List<@NotNull Long> playerIds,
                                    boolean teams, PartySettings settings) {
    }

    public record ThrowRequest(@NotNull Integer segment, @NotNull Integer multiplier, @NotNull @Min(0) Integer expectedDartCount) {
    }

    public record UndoRequest(@NotNull @Min(1) Integer expectedDartCount) {
    }

    public record PlayerSlot(int slot, int side, PlayerSummary player) {
    }

    /** The full scoreboard, pushed on /topic/games/{id} after every dart. {@code rated} is always false. */
    public record GameView(UUID id, GameType type, boolean teams, boolean rated, String status, PartySettings settings,
                           List<PlayerSlot> players, List<PartyState.SideState> sides, List<PartyState.SlotStats> slots,
                           int currentSlot, int leg, int round, List<String> turnDarts, List<String> checkout, String lastDart,
                           Integer winnerSide, int dartCount, Long createdBy, Instant createdAt, Instant completedAt) {
    }

    public record GameSummary(UUID id, GameType type, boolean teams, String status, List<PlayerSlot> players,
                              Integer winnerSide, Instant createdAt, Instant completedAt) {
    }

    /** Per game type for one player. {@code best}: X01 best average, Cricket best marks per round,
     *  Killer most lives taken in a game, Around the Clock fewest darts in a win (null if none). */
    public record TypeStats(GameType type, int played, int won, int darts, Double average, Double best) {
    }

    public record GameStats(PlayerSummary player, List<TypeStats> types) {
    }
}
