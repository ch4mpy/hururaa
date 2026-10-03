package pf.hururaa.uaa;

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
 * Their members are only changed through the endpoints designating administrators and managers,
 * never through the groups API, which refuses every change to a reserved group.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public final class DelegationGroups {

  /** The group of a direction's administrators. */
  public static final String ADMINS = HururaaPermission.RESERVED_PREFIX + "admins";

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

  /** Whether the group is one of Hurura'a's, whose roles and members the groups API can't change. */
  public static boolean isReserved(String groupName) {
    return groupName.startsWith(HururaaPermission.RESERVED_PREFIX);
  }
}
