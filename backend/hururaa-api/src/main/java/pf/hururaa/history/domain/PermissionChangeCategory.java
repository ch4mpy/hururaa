package pf.hururaa.history.domain;

/**
 * What a permission change is about: the categories the permission history is filtered by.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum PermissionChangeCategory {
  /** Directions created. */
  DIRECTION,
  /** Direction administrators and application managers designated or revoked. */
  DELEGATION,
  /** Applications registered in a direction, renamed, unregistered. */
  APPLICATION,
  /** Roles of an application defined or deleted. */
  APPLICATION_ROLE,
  /** Groups of a direction created or deleted. */
  GROUP,
  /** Application roles granted to a group, or revoked from it. */
  GROUP_ROLE,
  /** Users added to a group, or removed from it. */
  GROUP_MEMBER
}
