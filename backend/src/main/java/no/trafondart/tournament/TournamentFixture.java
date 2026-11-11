package no.trafondart.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tournament_fixtures")
public class TournamentFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false)
    private int position;

    @Column(name = "player1_id")
    private Long player1Id;

    @Column(name = "player2_id")
    private Long player2Id;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "winner_id")
    private Long winnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "next_fixture_id")
    private Long nextFixtureId;

    /** 1 or 2: which player slot of the next fixture the winner goes into. */
    @Column(name = "next_slot")
    private Short nextSlot;

    protected TournamentFixture() {
    }

    public TournamentFixture(UUID tournamentId, int round, int position, Long player1Id, Long player2Id) {
        this.tournamentId = tournamentId;
        this.round = round;
        this.position = position;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
    }

    /**
     * PENDING: waiting for players from earlier rounds. READY: both players known, can be played.
     * IN_PROGRESS: a match is being played. COMPLETED: has a winner. BYE: one player advanced without playing.
     */
    public enum Status { PENDING, READY, IN_PROGRESS, COMPLETED, BYE }

    public boolean hasBothPlayers() {
        return player1Id != null && player2Id != null;
    }

    public boolean involves(Long userId) {
        return userId.equals(player1Id) || userId.equals(player2Id);
    }

    public void setSlot(int slot, Long userId) {
        if (slot == 1) player1Id = userId;
        else player2Id = userId;
    }

    public Long getSlot(int slot) {
        return slot == 1 ? player1Id : player2Id;
    }

    public Long getId() { return id; }
    public UUID getTournamentId() { return tournamentId; }
    public int getRound() { return round; }
    public int getPosition() { return position; }
    public Long getPlayer1Id() { return player1Id; }
    public Long getPlayer2Id() { return player2Id; }
    public UUID getMatchId() { return matchId; }
    public void setMatchId(UUID matchId) { this.matchId = matchId; }
    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Long getNextFixtureId() { return nextFixtureId; }
    public Short getNextSlot() { return nextSlot; }

    public void linkNext(Long nextFixtureId, int slot) {
        this.nextFixtureId = nextFixtureId;
        this.nextSlot = (short) slot;
    }
}
