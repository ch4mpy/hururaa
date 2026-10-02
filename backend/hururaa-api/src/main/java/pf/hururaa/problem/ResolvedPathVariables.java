package pf.hururaa.problem;

import java.util.Arrays;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Path variables resolved into business objects by {@code Converter}s, so that
 * {@code @PreAuthorize} rules can be written against them.
 *
 * <p>
 * Such a converter owns not-found handling: an unknown id fails with a {@code 404 Not Found}
 * {@link HururaaProblemException} (unchecked-wrapped, as a {@code Converter} can't declare it)
 * before the endpoint method, and its access rule, are invoked (Spring MVC wraps it in a
 * type-mismatch exception, which {@link HururaaExceptionHandler} unwraps).
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public final class ResolvedPathVariables {

  private ResolvedPathVariables() {}

  /**
   * Documents the {@code 404} on every operation taking a parameter of one of {@code types}: it
   * never shows in the endpoint's {@code throws} clause, which is otherwise what springdoc derives
   * error responses from.
   */
  public static OperationCustomizer notFoundResponse(Class<?>... types) {
    final var resolved = Set.of(types);
    return (operation, handlerMethod) -> {
      if (Arrays.stream(handlerMethod.getMethodParameters())
          .anyMatch(p -> resolved.contains(p.getParameterType()))) {
        operation.getResponses().computeIfAbsent(String.valueOf(HttpStatus.NOT_FOUND.value()),
            _ -> new ApiResponse().description(HttpStatus.NOT_FOUND.getReasonPhrase())
                .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>()
                        .$ref(HururaaProblemDetail.class.getSimpleName())))));
      }
      return operation;
    };
  }
}
