package no.trafondart.game;

/**
 * One dart as it landed on the board.
 *
 * <ul>
 *   <li>{@code segment} 1–20 with {@code multiplier} 1 (single), 2 (double) or 3 (treble)</li>
 *   <li>{@code segment} 25 with {@code multiplier} 1 (outer bull, 25) or 2 (bullseye, 50 – counts as a double)</li>
 *   <li>{@code segment} 0 with {@code multiplier} 0 is a miss</li>
 * </ul>
 */
public record Dart(int segment, int multiplier) {

    public static final Dart MISS = new Dart(0, 0);
    public static final Dart OUTER_BULL = new Dart(25, 1);
    public static final Dart BULL = new Dart(25, 2);

    public Dart {
        if (!isValid(segment, multiplier)) {
            throw new IllegalArgumentException("Invalid dart: segment=" + segment + ", multiplier=" + multiplier);
        }
    }

    public static boolean isValid(int segment, int multiplier) {
        if (segment == 0) return multiplier == 0;
        if (segment == 25) return multiplier == 1 || multiplier == 2;
        return segment >= 1 && segment <= 20 && multiplier >= 1 && multiplier <= 3;
    }

    public static Dart single(int n) { return new Dart(n, 1); }
    public static Dart dbl(int n) { return new Dart(n, 2); }
    public static Dart treble(int n) { return new Dart(n, 3); }

    public int points() {
        return segment * multiplier;
    }

    public boolean isDouble() {
        return multiplier == 2;
    }

    public boolean isTreble() {
        return multiplier == 3;
    }

    public boolean isMiss() {
        return segment == 0;
    }

    /** Language-neutral notation used on the board and in checkout routes: S20, D16, T19, 25, BULL, MISS. */
    public String label() {
        if (segment == 0) return "MISS";
        if (segment == 25) return multiplier == 2 ? "BULL" : "25";
        return switch (multiplier) {
            case 1 -> "S" + segment;
            case 2 -> "D" + segment;
            default -> "T" + segment;
        };
    }

    @Override
    public String toString() {
        return label();
    }
}
