package pf.hururaa.uaa;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * Hurura'a's own roles: client roles of {@code hururaa-api}, the {@code roles-namespace} of this
 * API, granted like any application role through the groups of a direction.
 * </p>
 * <p>
 * They only take effect in the <b>platform organization</b> (see {@link UaaProperties}): holding
 * them in another direction grants nothing. Everything below the platform level (who administers a
 * direction, who manages an application) is not a token role but a delegation stored by Hurura'a,
 * see {@link pf.hururaa.direction.domain.DelegatedDirection}.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum HururaaPermission {
  /** Register applications, and set which direction manages each of them. */
  APPLICATIONS_MANAGE(Names.APPLICATIONS_MANAGE),

  /** Designate the administrators of every direction. */
  DIRECTION_ADMINS_MANAGE(Names.DIRECTION_ADMINS_MANAGE);

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

  public static final Set<String> ALL =
      Stream.of(HururaaPermission.values()).map(HururaaPermission::value).collect(Collectors.toSet());

  /**
   * The same names as compile-time constants: an annotation ({@code @PreAuthorize}) cannot call
   * {@link #value()}, and a literal repeated in access rules is how a permission name drifts.
   */
  public static final class Names {
    public static final String APPLICATIONS_MANAGE = "hururaa.applications.manage";
    public static final String DIRECTION_ADMINS_MANAGE = "hururaa.direction-admins.manage";

    private Names() {}
  }
}
