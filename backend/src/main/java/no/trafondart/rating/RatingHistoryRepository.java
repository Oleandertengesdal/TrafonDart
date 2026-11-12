package no.trafondart.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    List<RatingHistory> findAllByUserIdOrderByRatedAtAscIdAsc(Long userId);

    List<RatingHistory> findAllByMatchId(UUID matchId);

    List<RatingHistory> findAllByMatchIdIn(java.util.Collection<UUID> matchIds);

    List<RatingHistory> findAllByRatedAtGreaterThanEqualAndRatedAtLessThanOrderByRatedAtAscIdAsc(java.time.Instant start, java.time.Instant end);

    @Modifying
    @Query("delete from RatingHistory")
    void deleteEverything();
}
