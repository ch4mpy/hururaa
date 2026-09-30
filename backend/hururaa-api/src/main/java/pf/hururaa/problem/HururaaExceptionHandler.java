package pf.hururaa.problem;

import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns {@link HururaaProblemException} and validation failures into {@link HururaaProblemDetail}
 * bodies. Every other exception is left to Spring Boot's {@code ProblemDetailsExceptionHandler}
 * (enabled with {@code spring.mvc.problemdetails.enabled}).
 *
 * <p>
 * springdoc propagates the {@code @ApiResponse}s below to the operations
 * ({@code springdoc.override-with-generic-response}): those of a handler for an unchecked
 * exception to every operation, those of a handler for a checked exception only to the operations
 * declaring it in their {@code throws} clause. This is how {@link HururaaProblemDetail},
 * {@link ValidationProblemDetail} and {@link ProblemType} get into the OpenAPI spec, and why the
 * {@code 422} is declared on the unchecked validation handlers too (a
 * {@code MethodArgumentNotValidException} is checked, and no endpoint declares it).
 * </p>
 *
 * <p>
 * Highest precedence so that it is consulted before Boot's handler, which would otherwise catch
 * validation exceptions (as bare {@code 400}s) and the type-mismatch exceptions in which Spring
 * MVC wraps an {@link HururaaProblemException} thrown by a path-variable converter.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class HururaaExceptionHandler {

  static final String VALIDATION_DETAIL = "Validation failed";
  static final String SERVER_ERROR_DETAIL = "The request could not be completed because of a server-side failure";

  @ExceptionHandler(HururaaProblemException.class)
  @ApiResponses({
      @ApiResponse(responseCode = "400", description = "Bad Request",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = HururaaProblemDetail.class))),
      @ApiResponse(responseCode = "404", description = "Not Found",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = HururaaProblemDetail.class))),
      @ApiResponse(responseCode = "409", description = "Conflict",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = HururaaProblemDetail.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = HururaaProblemDetail.class)))})
  public ResponseEntity<HururaaProblemDetail> handleProblem(HururaaProblemException ex) {
    if (ex.getType().status().is5xxServerError()) {
      // A server-side failure's detail is written for the logs: with the Keycloak repositories,
      // it carries the response body of the admin API (realm and endpoint names, Keycloak's own
      // error messages). Same treatment as a data integrity violation: logged, not sent.
      log.error("{}: {}", ex.getType(), ex.getMessage());
      return problem(new HururaaProblemDetail(ex.getType(), SERVER_ERROR_DETAIL,
          ex.getProblem().getParameters()));
    }
    log.debug("{}: {}", ex.getType(), ex.getMessage());
    return problem(ex.getProblem());
  }

  /**
   * Optimistic-locking failure on a {@code @Version}ed entity: another request committed a change to
   * the same row in the meantime.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  @ApiResponse(responseCode = "409", description = "Conflict",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = HururaaProblemDetail.class)))
  public ResponseEntity<HururaaProblemDetail> handleConcurrentModification(
      OptimisticLockingFailureException ex) {
    final var parameters = new HashMap<String, Object>();
    if (ex instanceof ObjectOptimisticLockingFailureException objectEx) {
      Optional.ofNullable(objectEx.getPersistentClass()).map(Class::getSimpleName)
          .ifPresent(entity -> parameters.put("entity", entity));
      Optional.ofNullable(objectEx.getIdentifier()).ifPresent(id -> parameters.put("id", id));
    }
    log.debug("Concurrent modification: {}", ex.getMessage());
    return problem(new HururaaProblemDetail(ProblemType.CONCURRENT_MODIFICATION,
        "The resource was modified by another request in the meantime", parameters));
  }

  /**
   * A database constraint no business rule checked first. The exception message (SQL, table and
   * constraint names) is logged, not sent: only the constraint name is, when the driver reports it.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  @ApiResponse(responseCode = "409", description = "Conflict",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = HururaaProblemDetail.class)))
  public ResponseEntity<HururaaProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMessage());
    final var parameters = constraintName(ex)
        .<Map<String, Object>>map(name -> Map.of("constraint", name)).orElse(Map.of());
    return problem(new HururaaProblemDetail(ProblemType.DATA_INTEGRITY_VIOLATION,
        "The request conflicts with existing data", parameters));
  }

  /** {@code @Valid} request bodies. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ApiResponse(responseCode = "422", description = "Unprocessable Content",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ValidationProblemDetail.class)))
  public ResponseEntity<ValidationProblemDetail> handleBodyValidation(
      MethodArgumentNotValidException ex) {
    return validationProblem(ex.getBindingResult().getFieldErrors().stream()
        .collect(toMap(FieldError::getField, HururaaExceptionHandler::messageOf,
            HururaaExceptionHandler::join)));
  }

  /** Constraints on controller method parameters (Spring MVC's built-in method validation). */
  @ExceptionHandler(HandlerMethodValidationException.class)
  @ApiResponse(responseCode = "422", description = "Unprocessable Content",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ValidationProblemDetail.class)))
  public ResponseEntity<ValidationProblemDetail> handleParameterValidation(
      HandlerMethodValidationException ex) {
    return validationProblem(ex.getParameterValidationResults().stream()
        .flatMap(result -> result.getResolvableErrors().stream()
            .map(error -> Map.entry(pathOf(result, error), messageOf(error))))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, HururaaExceptionHandler::join)));
  }

  /** {@code @Validated} beans below the controller. */
  @ExceptionHandler(ConstraintViolationException.class)
  @ApiResponse(responseCode = "422", description = "Unprocessable Content",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ValidationProblemDetail.class)))
  public ResponseEntity<ValidationProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex) {
    return validationProblem(ex.getConstraintViolations().stream()
        .collect(toMap(cv -> cv.getPropertyPath().toString(), ConstraintViolation::getMessage,
            HururaaExceptionHandler::join)));
  }

  private static ResponseEntity<HururaaProblemDetail> problem(HururaaProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /** Hibernate reports the violated constraint's name (as declared in the Liquibase changesets). */
  private static Optional<String> constraintName(Throwable ex) {
    for (var cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernateEx) {
        return Optional.ofNullable(hibernateEx.getConstraintName());
      }
    }
    return Optional.empty();
  }

  private static ResponseEntity<ValidationProblemDetail> validationProblem(
      Map<String, String> invalidFields) {
    final var problem = new ValidationProblemDetail(VALIDATION_DETAIL, invalidFields);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  private static String pathOf(ParameterValidationResult result, MessageSourceResolvable error) {
    if (error instanceof FieldError fieldError) {
      return fieldError.getField();
    }
    final var parameter = result.getMethodParameter();
    return Objects.requireNonNullElseGet(parameter.getParameterName(),
        () -> "arg" + parameter.getParameterIndex());
  }

  private static String messageOf(MessageSourceResolvable error) {
    return Objects.requireNonNullElse(error.getDefaultMessage(), "invalid");
  }

  /** A property violating several constraints keeps all of its messages. */
  private static String join(String first, String second) {
    return first + "; " + second;
  }
}
