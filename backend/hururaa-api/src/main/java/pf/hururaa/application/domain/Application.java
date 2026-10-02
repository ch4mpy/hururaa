package pf.hururaa.application.domain;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.Nullable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * An application whose users are identified by Keycloak, and which is managed by a direction.
 *
 * <p>
 * Keycloak does not scope clients to an organization: which direction manages an application is
 * Hurura'a's own knowledge, and so is who, within that direction, may manage the application's
 * roles, groups and user assignments (its {@link #managers}).
 * </p>
 *
 * <p>
 * Its groups are organization groups of its direction named {@code <prefix>.<name>}
 * ({@code escales.agent}): a group grants roles of the application its name starts with, and of no
 * other. Client prefixes contain no dot, which makes that application unambiguous.
 * </p>
 *
 * <p>
 * Every application has two Keycloak clients named after its {@link #clientPrefix}: its users log
 * in with {@code <prefix>-bff} (authorization code with PKCE, refresh token), and its REST API
 * calls Keycloak's admin API with the service account of {@code <prefix>-api}, which also carries
 * the application's roles.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Entity
@Table(name = "APPLICATIONS",
    uniqueConstraints = @UniqueConstraint(name = "UK_APPLICATIONS_CLIENT_PREFIX",
        columnNames = "CLIENT_PREFIX"),
    indexes = @Index(name = "IDX_APPLICATIONS_DIRECTION", columnList = "DIRECTION"))
@Audited
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Application implements Serializable {
  private static final long serialVersionUID = -2203717815932264771L;

  /** Separates, in a group's name, the client prefix of its application from the rest. */
  public static final String GROUP_NAME_SEPARATOR = ".";

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "APPLICATIONS_SEQ")
  @SequenceGenerator(name = "APPLICATIONS_SEQ", sequenceName = "APPLICATIONS_SEQ",
      allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  /**
   * Optimistic-locking version: a concurrent update of the same row fails with
   * {@link pf.hururaa.problem.ProblemType#CONCURRENT_MODIFICATION}. Not audited (Envers default).
   */
  @Version
  @Column(name = "VERSION", nullable = false)
  private long version;

  /**
   * What the application's Keycloak client IDs start with ({@code escales} for
   * {@code escales-bff} and {@code escales-api}). Immutable: it identifies the clients.
   */
  @Column(name = "CLIENT_PREFIX", nullable = false, updatable = false)
  @ToString.Include
  private String clientPrefix;

  /** Display name (e.g. "Te Fenua"). */
  @Column(name = "NAME", nullable = false)
  @ToString.Include
  private String name;

  /** Alias of the Keycloak organization (direction) managing the application. */
  @Column(name = "DIRECTION", nullable = false)
  @ToString.Include
  private String direction;

  /**
   * IDs (Keycloak {@code sub}) of the members of {@link #direction} allowed to manage the
   * application's roles, the groups granting them and the members of those groups. Designated by
   * the direction's administrators.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "APPLICATION_MANAGERS",
      joinColumns = @JoinColumn(name = "APPLICATION_ID"),
      indexes = @Index(name = "IDX_APPLICATION_MANAGERS_USER_ID", columnList = "USER_ID"))
  @Column(name = "USER_ID", nullable = false)
  @Builder.Default
  private Set<String> managers = new HashSet<>();

  /** Whether the user was designated manager of the application. */
  public boolean isManagedBy(String userId) {
    return managers.contains(userId);
  }

  /** The full name of the application's group named {@code name}: {@code escales.agent}. */
  public String groupName(String name) {
    return clientPrefix + GROUP_NAME_SEPARATOR + name;
  }

  /** Whether the group with that name is one of the application's. */
  public boolean ownsGroup(String groupName) {
    return groupName.startsWith(clientPrefix + GROUP_NAME_SEPARATOR);
  }

  /**
   * @param applications the applications of the group's direction
   * @return the one the group belongs to, empty for a group created outside of Hurura'a
   */
  public static Optional<Application> owningGroup(Collection<Application> applications,
      String groupName) {
    return applications.stream().filter(application -> application.ownsGroup(groupName)).findAny();
  }
}
