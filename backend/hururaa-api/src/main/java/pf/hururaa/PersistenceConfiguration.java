/**
 *
 */
package pf.hururaa;

import java.io.Serializable;
import java.util.Date;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.envers.repository.config.EnableEnversRepositories;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@EnableTransactionManagement
@EnableEnversRepositories
public class PersistenceConfiguration {

  /**
   * Adds the username to revisions metadata.
   *
   * @see Revinfo
   */
  @Component
  static class SecurityAwareRevisionListener implements RevisionListener {

    @Override
    public void newRevision(@Nullable Object revisionEntity) {
      if (SecurityContextHolder.getContext().getAuthentication() instanceof Authentication auth
          && revisionEntity instanceof Revinfo rev) {
        rev.setUsername(auth.getName());
      }
    }
  }

  @Entity
  @Table(name = "REVINFO")
  @RevisionEntity(value = SecurityAwareRevisionListener.class)
  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Builder
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @ToString(onlyExplicitlyIncluded = true)
  public static class Revinfo implements Serializable {
    private static final long serialVersionUID = -5382427152828146876L;

    @Id
    @Column(name = "REV")
    @RevisionNumber
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "REVINFO_SEQ")
    @SequenceGenerator(name = "REVINFO_SEQ", sequenceName = "REVINFO_SEQ", allocationSize = 1)
    @EqualsAndHashCode.Include
    @ToString.Include
    private @Nullable Long id;

    @RevisionTimestamp
    @Column(name = "REVTSTMP")
    @Builder.Default
    private long timestamp = new Date().getTime();

    @Column(name = "USERNAME")
    private @Nullable String username;

  }
}
