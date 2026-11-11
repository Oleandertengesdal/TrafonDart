package no.trafondart.tournament;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentFixtureRepository extends JpaRepository<TournamentFixture, Long> {

    List<TournamentFixture> findAllByTournamentIdOrderByRoundAscPositionAsc(UUID tournamentId);

    Optional<TournamentFixture> findByMatchId(UUID matchId);
}
