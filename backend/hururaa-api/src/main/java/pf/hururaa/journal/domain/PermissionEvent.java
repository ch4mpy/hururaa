package pf.hururaa.journal.domain;

import java.io.Serializable;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * A permission change Hurura'a made in Keycloak, journaled since Keycloak attributes it to the
 * API's service account. Append-only: the current state is always read from Keycloak, this is its
 * history.
 *
 * <p>
 * Users are referenced by their Keycloak id and resolved when displayed; the application's name is
 * kept as it was, the application possibly unregistered since.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Entity
@Table(name = "PERMISSION_EVENTS",
    indexes = @Index(name = "IDX_PERMISSION_EVENTS_DIRECTION", columnList = "DIRECTION"))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PermissionEvent implements Serializable {
  private static final long serialVersionUID = 4263906175843140151L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "PERMISSION_EVENTS_SEQ")
  @SequenceGenerator(name = "PERMISSION_EVENTS_SEQ", sequenceName = "PERMISSION_EVENTS_SEQ",
      allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  @Column(name = "OCCURRED_AT", nullable = false, updatable = false)
  private Instant occurredAt;

  /** Keycloak id of who made the change. */
  @Column(name = "AUTHOR_ID", nullable = false, updatable = false)
  private String authorId;

  /** Alias of the direction concerned. */
  @Column(name = "DIRECTION", nullable = false, updatable = false)
  @ToString.Include
  private String direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "EVENT_TYPE", nullable = false, updatable = false)
  @ToString.Include
  private PermissionEventType type;

  /** For a role or group related event: the application whose role or group it is. */
  @Column(name = "APPLICATION_ID", updatable = false)
  private @Nullable Long applicationId;

  /** For a role or group related event: the application's name at the time of the change. */
  @Column(name = "APPLICATION_NAME", updatable = false)
  private @Nullable String applicationName;

  @Column(name = "ROLE_NAME", updatable = false)
  private @Nullable String role;

  @Column(name = "GROUP_NAME", updatable = false)
  private @Nullable String groupName;

  /**
   * For a group member or delegation event: Keycloak id of the member added or removed, of the
   * delegate designated or revoked.
   */
  @Column(name = "USER_ID", updatable = false)
  private @Nullable String userId;
}
