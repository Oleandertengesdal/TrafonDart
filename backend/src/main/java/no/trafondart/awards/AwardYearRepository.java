package no.trafondart.awards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AwardYearRepository extends JpaRepository<YearEntities.AwardYear, Integer> {

    @Modifying(flushAutomatically = true)
    @Query("delete from AwardYear y where y.year >= :year")
    int deleteFrom(@Param("year") int year);
}
