package pf.hururaa.history.domain;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum PermissionChangeType {
  DIRECTION_CREATED(PermissionChangeCategory.DIRECTION),

  DIRECTION_ADMIN_GRANTED(PermissionChangeCategory.DELEGATION),
  DIRECTION_ADMIN_REVOKED(PermissionChangeCategory.DELEGATION),
  APPLICATION_MANAGER_GRANTED(PermissionChangeCategory.DELEGATION),
  APPLICATION_MANAGER_REVOKED(PermissionChangeCategory.DELEGATION),

  APPLICATION_REGISTERED(PermissionChangeCategory.APPLICATION),
  APPLICATION_RENAMED(PermissionChangeCategory.APPLICATION),
  APPLICATION_UNREGISTERED(PermissionChangeCategory.APPLICATION),

  APPLICATION_ROLE_CREATED(PermissionChangeCategory.APPLICATION_ROLE),
  APPLICATION_ROLE_DELETED(PermissionChangeCategory.APPLICATION_ROLE),

  GROUP_CREATED(PermissionChangeCategory.GROUP),
  GROUP_DELETED(PermissionChangeCategory.GROUP),

  GROUP_ROLE_GRANTED(PermissionChangeCategory.GROUP_ROLE),
  GROUP_ROLE_REVOKED(PermissionChangeCategory.GROUP_ROLE),

  GROUP_MEMBER_ADDED(PermissionChangeCategory.GROUP_MEMBER),
  GROUP_MEMBER_REMOVED(PermissionChangeCategory.GROUP_MEMBER);

  private final PermissionChangeCategory category;

  PermissionChangeType(PermissionChangeCategory category) {
    this.category = category;
  }

  public PermissionChangeCategory category() {
    return category;
  }

  /** The types of the given categories, all of them for no category. */
  public static Set<PermissionChangeType> of(Set<PermissionChangeCategory> categories) {
    return Stream
        .of(values())
        .filter(type -> categories.isEmpty() || categories.contains(type.category))
        .collect(Collectors.toSet());
  }
}
