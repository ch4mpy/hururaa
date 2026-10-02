package pf.hururaa.journal.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pf.hururaa.journal.domain.PermissionEvent;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public interface PermissionEventRepository
    extends JpaRepository<PermissionEvent, Long>, JpaSpecificationExecutor<PermissionEvent> {

  Page<PermissionEvent> findByDirectionOrderByIdDesc(String direction, Pageable pageable);
}
