package pf.hururaa.uaa;

import java.util.Optional;
import java.util.Set;

/**
 * The groups carrying Hurura'a's delegations, in each direction: being a member of one of them is
 * holding the delegation. They are groups of the Hurura'a application (their names start with
 * {@value HururaaPermission#RESERVED_PREFIX}), present in every direction, created and deleted by
 * Hurura'a along with the directions and applications, and each grants a single
 * {@link HururaaPermission reserved role} which never changes:
 *
 * <ul>
 * <li>{@value #ADMINS}, in every direction, grants {@link HururaaPermission#DIRECTION_ADMIN}: its
 * members are the direction's administrators (in the DSI: the Hurura'a administrators);</li>
 * <li>{@code hururaa.<prefix>.product-owners}, in the direction of the application with that client
 * prefix, grants {@link HururaaPermission#applicationManager(String)
 * hururaa.application.<prefix>.manage}: its members are the application's managers.</li>
 * </ul>
 *
 * <p>
 * Their members are changed through the groups API, like those of any group of an application
 * (decision 0008): by the administrators of their direction and the managers of Hurura'a, and, for
 * {@code hururaa.<prefix>.product-owners}, by the managers of that application too. The groups
 * themselves and the roles they grant are never changed through the API.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public final class DelegationGroups {

  /** The group of a direction's administrators. */
  public static final String ADMINS = HururaaPermission.RESERVED_PREFIX + "admins";

  /**
   * The client prefix of the Hurura'a application, registered in the DSI and the only one
   * available in every direction: the delegation groups of every direction are its groups.
   */
  public static final String HURURAA_CLIENT_PREFIX = "hururaa";

  private static final String PRODUCT_OWNERS_SUFFIX = ".product-owners";

  /**
   * Words an application's client prefix can't be: they would make its clients, roles or groups
   * look like Hurura'a's own ({@code hururaa} is the Hurura'a application itself, registered with
   * the dev data and on deployment, never through the API).
   */
  public static final Set<String> RESERVED_CLIENT_PREFIXES =
      Set.of("hururaa", "admin", "direction", "product-owners", "manage");

  private DelegationGroups() {}

  /**
   * @param clientPrefix an application's client prefix
   * @return the group of that application's managers: {@code hururaa.escales.product-owners}
   */
  public static String productOwners(String clientPrefix) {
    return HururaaPermission.RESERVED_PREFIX + clientPrefix + PRODUCT_OWNERS_SUFFIX;
  }

  /**
   * Whether the group is one of Hurura'a's: the groups API changes its members, never the group
   * itself nor the roles it grants.
   */
  public static boolean isReserved(String groupName) {
    return groupName.startsWith(HururaaPermission.RESERVED_PREFIX);
  }

  /** Whether the group is the one of a direction's administrators. */
  public static boolean isAdmins(String groupName) {
    return ADMINS.equals(groupName);
  }

  /**
   * @param groupName a group's name
   * @return the client prefix of the application whose managers the group holds
   *         ({@code escales} for {@code hururaa.escales.product-owners}), empty for any other group
   */
  public static Optional<String> productOwnersPrefix(String groupName) {
    if (!groupName.startsWith(HururaaPermission.RESERVED_PREFIX)
        || !groupName.endsWith(PRODUCT_OWNERS_SUFFIX)) {
      return Optional.empty();
    }
    final var prefix = groupName.substring(HururaaPermission.RESERVED_PREFIX.length(),
        groupName.length() - PRODUCT_OWNERS_SUFFIX.length());
    return prefix.isEmpty() || prefix.contains(".") ? Optional.empty() : Optional.of(prefix);
  }
}
