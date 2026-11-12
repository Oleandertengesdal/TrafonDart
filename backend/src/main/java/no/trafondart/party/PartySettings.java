package no.trafondart.party;

import no.trafondart.game.GameConfig.InRule;
import no.trafondart.game.GameConfig.OutRule;

import java.util.List;

/**
 * Settings for a party game. Each game uses only some of the fields; {@link PartyRules#normalise} fills in defaults,
 * checks ranges and clears the fields a game doesn't use, so two equal games always have equal settings.
 *
 * @param startScore      X01: 301, 501 or 701
 * @param inRule          X01: straight or double in
 * @param outRule         X01: straight, double or master out
 * @param legsToWin       X01 and Cricket: 1–11
 * @param lives           Killer: 1–5
 * @param multiplierSkips Around the Clock: a double or treble on the target jumps 2 or 3 numbers ahead
 * @param roundLimit      Cricket: 0 (no limit) or 10–30 rounds, after which the most points wins the leg
 * @param killerNumbers   Killer: each player's number (1–20, all different), in throwing order
 */
public record PartySettings(Integer startScore, InRule inRule, OutRule outRule, Integer legsToWin,
                            Integer lives, Boolean multiplierSkips, Integer roundLimit, List<Integer> killerNumbers) {
}
