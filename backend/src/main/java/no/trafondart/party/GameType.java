package no.trafondart.party;

/** The party games. None of them affect ratings or Player of the Month. */
public enum GameType {
    /** 301/501/701 for 3–4 players, or 2 vs 2. */
    X01,
    /** 15–20 and bull: close them first, score on the ones others haven't closed. */
    CRICKET,
    /** Hit your own double to become a killer, then take the others' lives with theirs. */
    KILLER,
    /** 1 to 20 in order, then the bull. Also works solo, as practice. */
    AROUND_THE_CLOCK
}
