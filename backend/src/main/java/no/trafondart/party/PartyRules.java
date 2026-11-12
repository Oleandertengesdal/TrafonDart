package no.trafondart.party;

import no.trafondart.game.GameConfig;
import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;

import java.util.HashSet;
import java.util.List;

/** Who can play which game, and with which settings. Throws {@link IllegalArgumentException} for anything else. */
public final class PartyRules {

    private PartyRules() {
    }

    /** How many players (slots) a game allows. Doubles is always exactly four, two per team. */
    public static void checkLineup(GameType type, boolean teams, int players) {
        boolean ok = switch (type) {
            case X01 -> teams ? players == 4 : players >= 3 && players <= 4;
            case CRICKET -> teams ? players == 4 : players >= 2 && players <= 4;
            case KILLER -> !teams && players >= 2 && players <= 8;
            case AROUND_THE_CLOCK -> !teams && players >= 1 && players <= 8;
        };
        if (!ok) throw new IllegalArgumentException("Invalid number of players for " + type + (teams ? " (teams)" : ""));
    }

    public static PartySettings normalise(GameType type, boolean teams, int players, PartySettings in) {
        checkLineup(type, teams, players);
        PartySettings s = in == null ? new PartySettings(null, null, null, null, null, null, null, null) : in;
        return switch (type) {
            case X01 -> {
                int start = s.startScore() == null ? 501 : s.startScore();
                if (!GameConfig.ALLOWED_START_SCORES.contains(start)) throw new IllegalArgumentException("startScore");
                yield new PartySettings(start, s.inRule() == null ? InRule.STRAIGHT : s.inRule(),
                        s.outRule() == null ? OutRule.DOUBLE : s.outRule(), legs(s.legsToWin()), null, null, null, null);
            }
            case CRICKET -> {
                int limit = s.roundLimit() == null ? 20 : s.roundLimit();
                if (limit != 0 && (limit < 10 || limit > 30)) throw new IllegalArgumentException("roundLimit");
                yield new PartySettings(null, null, null, legs(s.legsToWin()), null, null, limit, null);
            }
            case KILLER -> {
                int lives = s.lives() == null ? 3 : s.lives();
                if (lives < 1 || lives > 5) throw new IllegalArgumentException("lives");
                List<Integer> numbers = s.killerNumbers();
                if (numbers == null || numbers.size() != players || new HashSet<>(numbers).size() != players
                        || numbers.stream().anyMatch(n -> n == null || n < 1 || n > 20)) {
                    throw new IllegalArgumentException("killerNumbers");
                }
                yield new PartySettings(null, null, null, null, lives, null, null, List.copyOf(numbers));
            }
            case AROUND_THE_CLOCK -> new PartySettings(null, null, null, null, null, Boolean.TRUE.equals(s.multiplierSkips()), null, null);
        };
    }

    private static int legs(Integer legsToWin) {
        int legs = legsToWin == null ? 1 : legsToWin;
        if (legs < 1 || legs > GameConfig.MAX_LEGS_TO_WIN) throw new IllegalArgumentException("legsToWin");
        return legs;
    }
}
