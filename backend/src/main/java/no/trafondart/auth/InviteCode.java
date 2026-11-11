package no.trafondart.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A registration invite. Only the SHA-256 hash of the code is stored; the plain code is shown once. */
@Entity
@Table(name = "invite_codes")
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "code_hint", nullable = false, length = 8)
    private String codeHint;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int uses;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(length = 100)
    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected InviteCode() {
    }

    public InviteCode(String codeHash, String codeHint, int maxUses, Instant expiresAt, String note, Long createdBy) {
        this.codeHash = codeHash;
        this.codeHint = codeHint;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
        this.note = note;
        this.createdBy = createdBy;
    }

    public enum Status { ACTIVE, EXPIRED, USED_UP, REVOKED }

    public Status status(Instant now) {
        if (revoked) return Status.REVOKED;
        if (uses >= maxUses) return Status.USED_UP;
        if (!expiresAt.isAfter(now)) return Status.EXPIRED;
        return Status.ACTIVE;
    }

    public Long getId() { return id; }
    public String getCodeHash() { return codeHash; }
    public String getCodeHint() { return codeHint; }
    public int getMaxUses() { return maxUses; }
    public int getUses() { return uses; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void revoke() { this.revoked = true; }
    public String getNote() { return note; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
