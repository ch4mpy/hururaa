package pf.hururaa.history.domain;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which permission changes of a direction to list.
 *
 * @param direction the direction's alias
 * @param applicationId only the changes concerning that application (its delegations, roles and
 *        the groups granting them)
 * @param group only the changes concerning that group (its roles and members)
 * @param categories only the changes of these categories, all of them when empty
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public record PermissionHistoryFilter(
    String direction,
    @Nullable Long applicationId,
    @Nullable String group,
    Set<PermissionChangeCategory> categories) {

  public PermissionHistoryFilter {
    categories = Set.copyOf(categories);
  }

  public static PermissionHistoryFilter ofDirection(String direction,
      Set<PermissionChangeCategory> categories) {
    return new PermissionHistoryFilter(direction, null, null, categories);
  }
}
