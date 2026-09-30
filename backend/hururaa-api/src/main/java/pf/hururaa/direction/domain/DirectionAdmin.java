package pf.hururaa.direction.domain;

import java.io.Serializable;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A member of a direction designated, by the platform administrators, to decide who manages each
 * of the direction's applications.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Entity
@Table(name = "DIRECTION_ADMINS",
    uniqueConstraints = @UniqueConstraint(name = "UK_DIRECTION_ADMINS_DIRECTION_USER_ID",
        columnNames = {"DIRECTION", "USER_ID"}),
    indexes = @Index(name = "IDX_DIRECTION_ADMINS_USER_ID", columnList = "USER_ID"))
@Audited
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class DirectionAdmin implements Serializable {
  private static final long serialVersionUID = 6187203553719540818L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "DIRECTION_ADMINS_SEQ")
  @SequenceGenerator(name = "DIRECTION_ADMINS_SEQ", sequenceName = "DIRECTION_ADMINS_SEQ",
      allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  /** Alias of the Keycloak organization (direction) administered. */
  @Column(name = "DIRECTION", nullable = false)
  @ToString.Include
  private String direction;

  /** The administrator's Keycloak user id (their tokens' {@code sub}). */
  @Column(name = "USER_ID", nullable = false)
  @ToString.Include
  private String userId;
}
