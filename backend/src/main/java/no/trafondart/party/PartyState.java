package no.trafondart.party;

import no.trafondart.game.Dart;

import java.util.List;
import java.util.Map;

/**
 * Everything about a party game after replaying its darts.
 *
 * @param currentSlot  whose turn it is (slot index, i.e. position in the throwing order)
 * @param leg          1-based leg number (always 1 for Killer and Around the Clock)
 * @param round        1-based round of the current leg, as seen by the current player
 * @param turnDarts    darts of the current, unfinished turn
 * @param checkout     X01: suggested finish for the current side, empty if none
 * @param winnerSide   null while playing
 */
public record PartyState(GameType type, int dartCount, int currentSlot, int leg, int round, List<Dart> turnDarts,
                         List<Dart> checkout, boolean finished, Integer winnerSide, Dart lastDart,
                         List<SideState> sides, List<SlotStats> slots) {

    /**
     * One player, or one team in doubles. Only the fields of the game being played are set.
     *
     * @param remaining X01 points left
     * @param opened    X01 double in: has this side started scoring
     * @param marks     Cricket marks (0–3) for 15–20 and 25
     * @param points    Cricket points
     * @param number    Killer: this player's number
     * @param lives     Killer lives left
     * @param killer    Killer: has hit their own double
     * @param out       Killer: no lives left
     * @param target    Around the Clock: the number to hit next (1–20, then 25)
     * @param finished  Around the Clock: has hit the bull
     */
    public record SideState(int side, int legsWon, Integer remaining, Boolean opened, Map<Integer, Integer> marks, Integer points,
                            Integer number, Integer lives, Boolean killer, Boolean out, Integer target, Boolean finished) {
    }

    /**
     * Per player. {@code score} means: X01 points scored, Cricket marks, Killer lives taken, Around the Clock targets hit.
     * {@code placement} is 1 for the winning side; set once the game is over (0 before).
     */
    public record SlotStats(int slot, int side, int darts, int score, int count180, int highestCheckout, int placement) {
    }
}
