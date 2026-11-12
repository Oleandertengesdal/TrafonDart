package no.trafondart.awards;

/**
 * The "month points" formula. Pure and documented in docs/AWARDS.md.
 *
 * <p>It deliberately rewards <b>getting better and playing a lot</b> rather than simply being the strongest:
 * rating <i>gained</i> this month, matches played, wins, improvement against your own earlier average,
 * highlights (180s, 100+ checkouts) and kudos from the others.
 */
public final class MonthlyScore {

    public static final double POINTS_PER_RATING = 1.0;
    public static final double RATING_FLOOR = -25;          // a bad month can't sink you more than this
    public static final double RATING_CAP = 60;             // new players' provisional swings can't run away with it
    public static final double POINTS_PER_MATCH = 4;
    public static final int MATCH_CAP = 20;                 // activity counts up to 20 matches
    public static final double POINTS_PER_WIN = 6;
    public static final double POINTS_PER_AVG_POINT = 2;    // per point of 3-dart average above your own baseline
    public static final double AVG_MIN = -20;
    public static final double AVG_MAX = 40;
    public static final double POINTS_PER_180 = 3;
    public static final double POINTS_PER_TON_CHECKOUT = 5; // checkout of 100 or more
    public static final int KUDOS_CAP = 10;                 // 1 point per kudos, up to 10

    private MonthlyScore() {
    }

    /** Everything one player did in one month. {@code priorAverage} is null for players with no earlier matches. */
    public record Input(int matches, int wins, Double ratingChange, double monthAverage, Double priorAverage,
                        int count180, int tonCheckouts, int highestCheckout, int kudos) {
    }

    /** Points per component, so the UI can show exactly why someone is on top. */
    public record Breakdown(double rating, double activity, double wins, double improvement, double highlights,
                            double kudos, double total) {
    }

    public static Breakdown score(Input in) {
        double rating = in.ratingChange() == null ? 0 : clamp(in.ratingChange() * POINTS_PER_RATING, RATING_FLOOR, RATING_CAP);
        double activity = Math.min(in.matches(), MATCH_CAP) * POINTS_PER_MATCH;
        double wins = in.wins() * POINTS_PER_WIN;
        double improvement = in.priorAverage() == null || in.matches() == 0 ? 0
                : clamp((in.monthAverage() - in.priorAverage()) * POINTS_PER_AVG_POINT, AVG_MIN, AVG_MAX);
        double highlights = in.count180() * POINTS_PER_180 + in.tonCheckouts() * POINTS_PER_TON_CHECKOUT;
        double kudos = Math.min(in.kudos(), KUDOS_CAP);
        double total = rating + activity + wins + improvement + highlights + kudos;
        return new Breakdown(r1(rating), r1(activity), r1(wins), r1(improvement), r1(highlights), r1(kudos), r1(total));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double r1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
