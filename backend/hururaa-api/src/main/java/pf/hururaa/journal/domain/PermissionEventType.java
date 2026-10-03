package pf.hururaa.journal.domain;

/**
 * The permission changes Hurura'a makes in Keycloak, which Keycloak itself can't attribute to the
 * person behind them (it sees the API's service account).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum PermissionEventType {
  DIRECTION_CREATED,
  DIRECTION_ADMIN_GRANTED,
  DIRECTION_ADMIN_REVOKED,
  APPLICATION_MANAGER_GRANTED,
  APPLICATION_MANAGER_REVOKED,
  APPLICATION_ROLE_CREATED,
  APPLICATION_ROLE_DELETED,
  GROUP_CREATED,
  GROUP_DELETED,
  GROUP_ROLE_GRANTED,
  GROUP_ROLE_REVOKED,
  GROUP_MEMBER_ADDED,
  GROUP_MEMBER_REMOVED
}
