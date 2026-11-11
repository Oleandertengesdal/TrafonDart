package no.trafondart.tournament;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, TournamentParticipant.Key> {

    @Query("select p from TournamentParticipant p where p.id.tournamentId = :tid order by p.seed")
    List<TournamentParticipant> findByTournament(@Param("tid") UUID tournamentId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TournamentParticipant p where p.id.tournamentId = :tid")
    void deleteByTournament(@Param("tid") UUID tournamentId);

    @Query("select count(p) from TournamentParticipant p where p.id.tournamentId = :tid")
    long countByTournament(@Param("tid") UUID tournamentId);
}
