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
   * Parameters: {@code applicationId}, {@code direction}. A group only grants the roles of the
   * applications managed by its own direction.
   */
  APPLICATION_NOT_IN_DIRECTION(HttpStatus.CONFLICT, "application-not-in-direction"),

  /**
   * Parameters: {@code applicationId}, {@code groups} (comma-separated names). The application
   * can't leave its direction (moved to another one, or unregistered) while groups of that
   * direction still grant its roles.
   */
  APPLICATION_ROLES_STILL_GRANTED(HttpStatus.CONFLICT, "application-roles-still-granted"),

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
