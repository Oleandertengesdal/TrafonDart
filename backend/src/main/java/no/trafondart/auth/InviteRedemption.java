package no.trafondart.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "invite_code_redemptions")
public class InviteRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_code_id", nullable = false)
    private Long inviteCodeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    protected InviteRedemption() {
    }

    public InviteRedemption(Long inviteCodeId, Long userId) {
        this.inviteCodeId = inviteCodeId;
        this.userId = userId;
    }

    public Long getInviteCodeId() { return inviteCodeId; }
    public Long getUserId() { return userId; }
    public Instant getRedeemedAt() { return redeemedAt; }
}
