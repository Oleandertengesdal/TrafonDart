package no.trafondart.awards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface YearlyAwardRepository extends JpaRepository<YearEntities.YearlyAward, YearEntities.YearlyAwardId> {

    @Query("select a from YearlyAward a order by a.id.year desc")
    List<YearEntities.YearlyAward> findAllNewestFirst();

    @Query("select a from YearlyAward a where a.userId = :userId order by a.id.year desc")
    List<YearEntities.YearlyAward> findByUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from YearlyAward a where a.id.year >= :year")
    int deleteFrom(@Param("year") int year);
}
