package pf.hururaa.application.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import pf.hururaa.application.domain.Application;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public interface ApplicationRepository
    extends JpaRepository<Application, Long>, RevisionRepository<Application, Long, Long> {

  List<Application> findAllByOrderByNameAsc();

  List<Application> findByDirectionOrderByNameAsc(String direction);

  boolean existsByClientPrefix(String clientPrefix);

  /** The applications the user manages, whatever their direction. */
  @Query("select a from Application a where :userId member of a.managers order by a.name")
  List<Application> findByManager(@Param("userId") String userId);

  /** Whether the user manages at least one of the direction's applications. */
  @Query("select count(a) > 0 from Application a"
      + " where a.direction = :direction and :userId member of a.managers")
  boolean existsByDirectionAndManager(
      @Param("direction") String direction,
      @Param("userId") String userId);
}
