package pf.hururaa.application.web;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.problem.HururaaProblemDetail;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;

/**
 * Resolves {@code @PathVariable} ids into {@link Application} entities, so that
 * {@code @PreAuthorize} rules can be written against the resolved application (its direction and
 * managers).
 *
 * <p>
 * The converter owns not-found handling: an unknown (or non-numeric) id fails with a
 * {@code 404 Not Found} {@link HururaaProblemException} (unchecked-wrapped, as a {@code Converter}
 * can't declare it) before the endpoint method is invoked (Spring MVC wraps it in a type-mismatch
 * exception, which {@link pf.hururaa.problem.HururaaExceptionHandler} unwraps). This is why Spring
 * Data's auto-registered {@code DomainClassConverter} isn't relied upon: it resolves an unknown id
 * to {@code null}, which the SpEL access rules can't evaluate.
 * </p>
 *
 * <p>
 * The repository is looked up lazily: every {@code WebMvcConfigurer} is part of any
 * {@code @WebMvcTest} slice, including those of controllers that neither need nor mock it.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationWebMvcConfiguration implements WebMvcConfigurer {

  private final ObjectProvider<ApplicationRepository> applicationRepository;

  /**
   * Documents the {@code 404} the converter can produce on every operation taking a resolved
   * {@link Application}: it never shows in the endpoint's {@code throws} clause, which is otherwise
   * what springdoc derives error responses from.
   */
  @Bean
  OperationCustomizer resolvedApplicationNotFoundResponse() {
    return (Operation operation, HandlerMethod handlerMethod) -> {
      if (Arrays.stream(handlerMethod.getMethodParameters())
          .anyMatch(p -> Application.class.equals(p.getParameterType()))) {
        operation.getResponses().computeIfAbsent(String.valueOf(HttpStatus.NOT_FOUND.value()),
            code -> new ApiResponse().description(HttpStatus.NOT_FOUND.getReasonPhrase())
                .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>()
                        .$ref(HururaaProblemDetail.class.getSimpleName())))));
      }
      return operation;
    };
  }

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new ApplicationConverter(applicationRepository));
  }

  @RequiredArgsConstructor
  static class ApplicationConverter implements Converter<String, Application> {
    private final ObjectProvider<ApplicationRepository> repository;

    @Override
    public Application convert(String id) {
      return parseId(id).flatMap(repository.getObject()::findById)
          .orElseThrow(() -> new HururaaProblemException(ProblemType.APPLICATION_NOT_FOUND,
              "No application with id " + id, Map.of("applicationId", id)).unchecked());
    }
  }

  /** Empty when not numeric: such an id can't match anything, which is a not-found too. */
  private static Optional<Long> parseId(String id) {
    try {
      return Optional.of(Long.valueOf(id));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
