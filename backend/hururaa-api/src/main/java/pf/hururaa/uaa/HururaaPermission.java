package pf.hururaa.uaa;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import pf.hururaa.commons.security.HururaaAuthentication;

/**
 * <p>
 * Hurura'a's own roles: client roles of {@code hururaa-api}, the {@code roles-namespace} of this
 * API, granted like any application role through the groups of a direction, and effective in that
 * direction only. They are Hurura'a's delegations (see {@link DelegationGroups}, which carries
 * them):
 * </p>
 * <ul>
 * <li>{@link #DIRECTION_ADMIN} administers the direction it is held in. Held in the DSI (see
 * {@link UaaProperties}), it makes a Hurura'a administrator, acting in every direction;</li>
 * <li>{@link #applicationManager(String) hururaa.application.<prefix>.manage} manages the
 * application with that client prefix, held in the application's direction.</li>
 * </ul>
 *
 * <p>
 * Every role whose name starts with {@value #RESERVED_PREFIX} is reserved: Hurura'a creates them
 * with the directions and applications, and no endpoint creates, deletes or grants them.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum HururaaPermission {
  /**
   * Administer the direction the role is held in: register its applications, define their roles
   * and managers, manage their groups. In the DSI: act in every direction, create directions and
   * designate their administrators.
   */
  DIRECTION_ADMIN(Names.DIRECTION_ADMIN);

  /** What the names of Hurura'a's roles and groups start with. */
  public static final String RESERVED_PREFIX = "hururaa.";

  private static final String APPLICATION_MANAGER_PREFIX = RESERVED_PREFIX + "application.";

  private static final String APPLICATION_MANAGER_SUFFIX = ".manage";

  private final String value;

  HururaaPermission(String value) {
    this.value = value;
  }

  /** The permission as it appears in the {@code organization} claim (the Keycloak role name). */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  /**
   * The static Hurura'a roles: those held in the DSI become the authorities of the
   * {@code Authentication}, which {@code hasAuthority(...)} rules check (see
   * {@link pf.hururaa.security.HururaaAuthenticationConverter}).
   */
  public static final Set<String> ALL =
      Stream.of(HururaaPermission.values()).map(HururaaPermission::value).collect(Collectors.toSet());

  /**
   * @param clientPrefix an application's client prefix
   * @return the role managing that application: {@code hururaa.application.escales.manage}
   */
  public static String applicationManager(String clientPrefix) {
    return APPLICATION_MANAGER_PREFIX + clientPrefix + APPLICATION_MANAGER_SUFFIX;
  }

  /** Whether the role is one of Hurura'a's, which only Hurura'a creates and grants. */
  public static boolean isReserved(String role) {
    return role.startsWith(RESERVED_PREFIX);
  }

  /**
   * @param authentication the security-context authentication
   * @param direction a direction alias
   * @return the roles the user holds in that direction (none for an anonymous user, or one who is
   *         not a member)
   */
  public static Set<String> heldIn(@Nullable Authentication authentication, String direction) {
    if (authentication instanceof HururaaAuthentication hururaaAuthentication) {
      return hururaaAuthentication.getPermissionsByTenant().getOrDefault(direction, Set.of());
    }
    return Set.of();
  }

  /**
   * @param authentication the security-context authentication
   * @return the aliases of the directions the user holds {@link #DIRECTION_ADMIN} in
   */
  public static Set<String> administeredDirections(@Nullable Authentication authentication) {
    if (!(authentication instanceof HururaaAuthentication hururaaAuthentication)) {
      return Set.of();
    }
    return hururaaAuthentication
        .getPermissionsByTenant()
        .entrySet()
        .stream()
        .filter(tenant -> tenant.getValue().contains(Names.DIRECTION_ADMIN))
        .map(tenant -> tenant.getKey())
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * The same names as compile-time constants: an annotation ({@code @PreAuthorize}) cannot call
   * {@link #value()}, and a literal repeated in access rules is how a permission name drifts.
   */
  public static final class Names {
    /**
     * As an authority ({@code hasAuthority(...)}), held in the DSI: a Hurura'a administrator.
     */
    public static final String DIRECTION_ADMIN = RESERVED_PREFIX + "direction.admin";

    private Names() {}
  }
}
