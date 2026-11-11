package no.trafondart.rating;

import java.util.List;

/**
 * Glicko-2 rating maths, following Mark Glickman's paper "Example of the Glicko-2 system" (2013).
 *
 * <p>Pure functions only; persistence and match ordering live in {@link RatingService}.
 */
public final class Glicko2 {

    public static final double DEFAULT_RATING = 1500.0;
    public static final double DEFAULT_RD = 350.0;
    public static final double DEFAULT_VOLATILITY = 0.06;
    /** System constant: how much volatility may change. 0.3–1.2 is sensible; 0.5 is a common default. */
    public static final double TAU = 0.5;
    /** RD above which a rating is shown as provisional. */
    public static final double PROVISIONAL_RD = 110.0;

    private static final double SCALE = 173.7178;
    private static final double EPSILON = 0.000001;

    private Glicko2() {
    }

    public record Rating(double rating, double rd, double volatility) {
        public static Rating initial() {
            return new Rating(DEFAULT_RATING, DEFAULT_RD, DEFAULT_VOLATILITY);
        }
    }

    /** One game in a rating period: the opponent's rating before the period and the score (1 win, 0 loss, 0.5 draw). */
    public record Result(Rating opponent, double score) {
    }

    /**
     * Grows RD for {@code idlePeriods} rating periods without games (Glickman step 6 applied repeatedly),
     * capped at the default RD.
     */
    public static Rating inflateForInactivity(Rating r, double idlePeriods) {
        if (idlePeriods <= 0) return r;
        double phi = r.rd() / SCALE;
        double inflated = Math.sqrt(phi * phi + idlePeriods * r.volatility() * r.volatility()) * SCALE;
        return new Rating(r.rating(), Math.min(inflated, DEFAULT_RD), r.volatility());
    }

    /** Updates a player's rating after one rating period containing {@code results}. */
    public static Rating update(Rating player, List<Result> results) {
        double mu = (player.rating() - DEFAULT_RATING) / SCALE;
        double phi = player.rd() / SCALE;
        double sigma = player.volatility();

        if (results.isEmpty()) {
            double phiStar = Math.sqrt(phi * phi + sigma * sigma);
            return new Rating(player.rating(), Math.min(phiStar * SCALE, DEFAULT_RD), sigma);
        }

        // Step 3 & 4: estimated variance v and improvement delta.
        double vInv = 0;
        double deltaSum = 0;
        for (Result res : results) {
            double muJ = (res.opponent().rating() - DEFAULT_RATING) / SCALE;
            double phiJ = res.opponent().rd() / SCALE;
            double g = g(phiJ);
            double e = expected(mu, muJ, g);
            vInv += g * g * e * (1 - e);
            deltaSum += g * (res.score() - e);
        }
        double v = 1.0 / vInv;
        double delta = v * deltaSum;

        // Step 5: new volatility (Illinois algorithm).
        double sigmaPrime = newVolatility(phi, sigma, v, delta);

        // Step 6 & 7: new RD and rating.
        double phiStar = Math.sqrt(phi * phi + sigmaPrime * sigmaPrime);
        double phiPrime = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double muPrime = mu + phiPrime * phiPrime * deltaSum;

        return new Rating(muPrime * SCALE + DEFAULT_RATING, Math.min(phiPrime * SCALE, DEFAULT_RD), sigmaPrime);
    }

    /** Win probability of {@code a} against {@code b}. */
    public static double winProbability(Rating a, Rating b) {
        double muA = (a.rating() - DEFAULT_RATING) / SCALE;
        double muB = (b.rating() - DEFAULT_RATING) / SCALE;
        double phi = Math.sqrt(Math.pow(a.rd() / SCALE, 2) + Math.pow(b.rd() / SCALE, 2));
        return expected(muA, muB, g(phi));
    }

    private static double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + 3.0 * phi * phi / (Math.PI * Math.PI));
    }

    private static double expected(double mu, double muJ, double g) {
        return 1.0 / (1.0 + Math.exp(-g * (mu - muJ)));
    }

    private static double newVolatility(double phi, double sigma, double v, double delta) {
        double a = Math.log(sigma * sigma);
        double phi2 = phi * phi;
        double delta2 = delta * delta;

        java.util.function.DoubleUnaryOperator f = x -> {
            double ex = Math.exp(x);
            double num = ex * (delta2 - phi2 - v - ex);
            double den = 2.0 * Math.pow(phi2 + v + ex, 2);
            return num / den - (x - a) / (TAU * TAU);
        };

        double bigA = a;
        double bigB;
        if (delta2 > phi2 + v) {
            bigB = Math.log(delta2 - phi2 - v);
        } else {
            int k = 1;
            while (f.applyAsDouble(a - k * TAU) < 0) k++;
            bigB = a - k * TAU;
        }
        double fA = f.applyAsDouble(bigA);
        double fB = f.applyAsDouble(bigB);
        int guard = 0;
        while (Math.abs(bigB - bigA) > EPSILON && guard++ < 1000) {
            double c = bigA + (bigA - bigB) * fA / (fB - fA);
            double fC = f.applyAsDouble(c);
            if (fC * fB <= 0) {
                bigA = bigB;
                fA = fB;
            } else {
                fA = fA / 2.0;
            }
            bigB = c;
            fB = fC;
        }
        return Math.exp(bigA / 2.0);
    }
}
