package pf.hururaa.problem;

import java.net.URI;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The closed set of problems this API reports, exposed in the OpenAPI spec as an enum of
 * {@code type} URIs so that the generated clients hold the translation keys at compile time.
 *
 * <p>
 * The parameters listed on each constant are the keys of
 * {@link HururaaProblemDetail#getParameters()} the frontend can interpolate in its localized
 * message.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum ProblemType {
  /** Parameters: none. Invalid fields are in {@link ValidationProblemDetail#getInvalidFields()}. */
  VALIDATION(HttpStatus.UNPROCESSABLE_CONTENT, "validation"),

  /** Parameters: {@code direction}. No Keycloak organization has that alias. */
  DIRECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "direction-not-found"),

  /** Parameters: {@code direction}. An organization already has that alias, or that name. */
  DIRECTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "direction-already-exists"),

  /** Parameters: {@code direction}, {@code group}. */
  GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "group-not-found"),

  /** Parameters: {@code userId}. The user is not a member of the direction. */
  NOT_A_MEMBER(HttpStatus.NOT_FOUND, "not-a-member"),

  /** Parameters: {@code applicationId}. */
  APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "application-not-found"),

  /** Parameters: {@code clientPrefix}. Another application already uses that client prefix. */
  APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "application-already-exists"),

  /** Parameters: {@code clientId}, {@code role}. The application's client has no such role. */
  APPLICATION_ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "application-role-not-found"),

  /**
   * Parameters: {@code direction}, {@code group}. The group's name does not start with the client
   * prefix of an application of its direction (it was created outside of Hurura'a): it grants no
   * role through Hurura'a.
   */
  GROUP_WITHOUT_APPLICATION(HttpStatus.CONFLICT, "group-without-application"),

  /**
   * Parameters: {@code applicationId}, {@code groups} (comma-separated names). The application
   * can't be unregistered while it has groups.
   */
  APPLICATION_HAS_GROUPS(HttpStatus.CONFLICT, "application-has-groups"),

  /**
   * Parameters: {@code name}. The name (an application's client prefix, a group, a role) is
   * reserved to Hurura'a's delegations ({@code hururaa.admins}, {@code hururaa.direction.admin}...):
   * only Hurura'a creates, changes or deletes those, along with the directions and applications.
   */
  RESERVED_NAME(HttpStatus.CONFLICT, "reserved-name"),

  /**
   * Parameters: {@code entity} (simple class name) and {@code id} when known. Another request
   * modified the same resource in the meantime: reload it before retrying.
   */
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "concurrent-modification"),

  /**
   * Parameters: {@code constraint} (the violated constraint's name) when the database reports it.
   * Safety net for a database constraint no business rule checked first (typically a race between
   * two requests).
   */
  DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "data-integrity-violation"),

  /**
   * Parameters: none. A REST call to the identity provider (Keycloak, where directions, users,
   * groups and application roles live) failed.
   */
  IDENTITY_PROVIDER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "identity-provider-error");

  private static final String NAMESPACE = "urn:hururaa:problem:";

  private final HttpStatus status;

  private final URI uri;

  ProblemType(HttpStatus status, String slug) {
    this.status = status;
    this.uri = URI.create(NAMESPACE + slug);
  }

  public HttpStatus status() {
    return status;
  }

  /** The problem {@code type}: what is serialized, and what the OpenAPI enum values are. */
  @JsonValue
  public URI uri() {
    return uri;
  }
}
