package no.trafondart.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.trafondart.rating.Glicko2;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String username;

    @Column(name = "real_name", nullable = false, length = 60)
    private String realName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Bumped to revoke every outstanding session token for this user. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "preferred_locale", length = 5)
    private String preferredLocale;

    @Column(nullable = false)
    private double rating = Glicko2.DEFAULT_RATING;

    @Column(name = "rating_deviation", nullable = false)
    private double ratingDeviation = Glicko2.DEFAULT_RD;

    @Column(nullable = false)
    private double volatility = Glicko2.DEFAULT_VOLATILITY;

    @Column(name = "last_rated_at")
    private Instant lastRatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    public User(String username, String realName, String passwordHash, Role role) {
        this.username = username;
        this.realName = realName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public enum Role { USER, ADMIN }

    public Glicko2.Rating glicko() {
        return new Glicko2.Rating(rating, ratingDeviation, volatility);
    }

    public void applyRating(Glicko2.Rating r, Instant at) {
        this.rating = r.rating();
        this.ratingDeviation = r.rd();
        this.volatility = r.volatility();
        this.lastRatedAt = at;
    }

    public void resetRating() {
        applyRating(Glicko2.Rating.initial(), null);
    }

    public void revokeSessions() {
        tokenVersion++;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTokenVersion() { return tokenVersion; }
    public String getPreferredLocale() { return preferredLocale; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }
    public double getRating() { return rating; }
    public double getRatingDeviation() { return ratingDeviation; }
    public double getVolatility() { return volatility; }
    public Instant getLastRatedAt() { return lastRatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
