package no.trafondart.match;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DartRepository extends JpaRepository<ThrownDart, Long> {

    List<ThrownDart> findAllByMatchIdOrderBySeqAsc(UUID matchId);

    Optional<ThrownDart> findFirstByMatchIdOrderBySeqDesc(UUID matchId);

    List<ThrownDart> findAllByMatchIdInOrderByMatchIdAscSeqAsc(Collection<UUID> matchIds);

    @Modifying
    @Query("delete from ThrownDart d where d.matchId = :matchId")
    void deleteAllByMatch(@Param("matchId") UUID matchId);
}
