package no.trafondart.bot;

/**
 * How good Trafo, the practice bot, is. Each level is a steadiness of hand: the spread (standard deviation, in mm) of
 * its darts around the point it aims at. The typical 3-dart averages are what that spread gives over many legs of 501,
 * checked by simulation in BotPlayerTest.
 */
public enum BotLevel {
    BEGINNER(34, 30),
    CASUAL(22, 42),
    CLUB(16, 55),
    LEAGUE(12, 70),
    PRO(8.5, 88);

    private final double sigma;
    private final int typicalAverage;

    BotLevel(double sigma, int typicalAverage) {
        this.sigma = sigma;
        this.typicalAverage = typicalAverage;
    }

    /** Spread of the darts around the aim point, in mm. */
    public double sigma() {
        return sigma;
    }

    /** The 3-dart average the level plays at over many legs of 501. */
    public int typicalAverage() {
        return typicalAverage;
    }
}
