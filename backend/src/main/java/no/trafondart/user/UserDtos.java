package no.trafondart.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.trafondart.rating.Glicko2;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    /** Usernames: 3–24 chars, letters/digits/underscore/dot/hyphen. Shared by registration. */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_.-]{3,24}$";
    /** Real names: letters (incl. æøå and other accents), spaces, apostrophes, hyphens and dots. */
    public static final String REAL_NAME_PATTERN = "^[\\p{L}][\\p{L} .'-]{0,59}$";

    /** Public view of a player, safe to show to any logged-in member. */
    public record PlayerSummary(Long id, String username, String realName, int rating, int rd, boolean provisional) {
        public static PlayerSummary of(User u) {
            return new PlayerSummary(u.getId(), u.getUsername(), u.getRealName(),
                    (int) Math.round(u.getRating()), (int) Math.round(u.getRatingDeviation()),
                    u.getRatingDeviation() > Glicko2.PROVISIONAL_RD);
        }
    }

    /** The logged-in user's own view. */
    public record Me(Long id, String username, String realName, String role, String preferredLocale,
                     int rating, int rd, boolean provisional) {
        public static Me of(User u) {
            return new Me(u.getId(), u.getUsername(), u.getRealName(), u.getRole().name(), u.getPreferredLocale(),
                    (int) Math.round(u.getRating()), (int) Math.round(u.getRatingDeviation()),
                    u.getRatingDeviation() > Glicko2.PROVISIONAL_RD);
        }
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 60) @Pattern(regexp = REAL_NAME_PATTERN) String realName,
            @Pattern(regexp = "^(nb|en)$") String preferredLocale) {
    }

    /** Admin view of a user. */
    public record AdminUserView(Long id, String username, String realName, String role, boolean enabled,
                                int rating, Instant createdAt) {
        public static AdminUserView of(User u) {
            return new AdminUserView(u.getId(), u.getUsername(), u.getRealName(), u.getRole().name(), u.isEnabled(),
                    (int) Math.round(u.getRating()), u.getCreatedAt());
        }
    }

    public record AdminUpdateUserRequest(@Pattern(regexp = "^(USER|ADMIN)$") String role, Boolean enabled) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 10, max = 72) String newPassword) {
    }
}
