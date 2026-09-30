package pf.hururaa.direction.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pf.hururaa.direction.domain.DirectionAdmin;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public interface DirectionAdminRepository extends JpaRepository<DirectionAdmin, Long> {

  List<DirectionAdmin> findByDirectionOrderByUserId(String direction);

  List<DirectionAdmin> findByUserIdOrderByDirection(String userId);

  Optional<DirectionAdmin> findByDirectionAndUserId(String direction, String userId);

  boolean existsByDirectionAndUserId(String direction, String userId);
}
