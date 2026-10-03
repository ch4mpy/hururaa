package pf.hururaa.application.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.history.RevisionRepository;
import pf.hururaa.application.domain.Application;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public interface ApplicationRepository
    extends JpaRepository<Application, Long>, RevisionRepository<Application, Long, Long> {

  List<Application> findAllByOrderByNameAsc();

  List<Application> findByDirectionOrderByNameAsc(String direction);

  boolean existsByClientPrefix(String clientPrefix);
}
