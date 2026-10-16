package pf.hururaa.application.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import pf.hururaa.application.domain.Application;
import pf.hururaa.uaa.DelegationGroups;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public interface ApplicationRepository
    extends JpaRepository<Application, Long>, RevisionRepository<Application, Long, Long> {

  List<Application> findAllByOrderByNameAsc();

  List<Application> findByDirectionOrderByNameAsc(String direction);

  /**
   * @param direction a direction alias
   * @return the applications addressed under that direction, by name: its own and Hurura'a (see
   *         {@link Application#isAvailableIn(String)})
   */
  @Query("select a from Application a where a.direction = :direction or a.clientPrefix = '"
      + DelegationGroups.HURURAA_CLIENT_PREFIX + "' order by a.name asc")
  List<Application> findAvailableIn(String direction);

  boolean existsByClientPrefix(String clientPrefix);
}
