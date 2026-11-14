package no.trafondart.bot;

import no.trafondart.game.Dart;

/**
 * A regulation dartboard in millimetres, centre at (0, 0), 20 at the top, y pointing up. Used to turn the point where a
 * bot's dart lands into what it scores.
 */
public final class Dartboard {

    /** Segments clockwise from the top. */
    static final int[] ORDER = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};

    static final double INNER_BULL = 6.35;
    static final double OUTER_BULL = 15.9;
    static final double TREBLE_INNER = 99;
    static final double TREBLE_OUTER = 107;
    static final double DOUBLE_INNER = 162;
    static final double DOUBLE_OUTER = 170;

    private Dartboard() {
    }

    /** What a dart landing at (x, y) scores. */
    public static Dart hit(double x, double y) {
        double r = Math.hypot(x, y);
        if (r <= INNER_BULL) return Dart.BULL;
        if (r <= OUTER_BULL) return Dart.OUTER_BULL;
        if (r > DOUBLE_OUTER) return Dart.MISS;
        int segment = segmentAt(Math.toDegrees(Math.atan2(y, x)));
        if (r >= TREBLE_INNER && r <= TREBLE_OUTER) return Dart.treble(segment);
        if (r >= DOUBLE_INNER) return Dart.dbl(segment);
        return Dart.single(segment);
    }

    private static int segmentAt(double degrees) {
        double fromTop = ((90 - degrees + 9) % 360 + 360) % 360;
        return ORDER[(int) (fromTop / 18) % 20];
    }

    /** The point to aim at for a target: the middle of its bed (the big outer single for a single). */
    public static double[] aimPoint(Dart target) {
        if (target.equals(Dart.BULL)) return new double[]{0, 0};
        if (target.equals(Dart.OUTER_BULL)) return new double[]{0, (INNER_BULL + OUTER_BULL) / 2};
        int index = 0;
        while (ORDER[index] != target.segment()) index++;
        double degrees = Math.toRadians(90 - index * 18);
        double r = switch (target.multiplier()) {
            case 3 -> (TREBLE_INNER + TREBLE_OUTER) / 2;
            case 2 -> (DOUBLE_INNER + DOUBLE_OUTER) / 2;
            default -> (TREBLE_OUTER + DOUBLE_INNER) / 2;
        };
        return new double[]{r * Math.cos(degrees), r * Math.sin(degrees)};
    }
}
