package pf.hururaa.application.web;

import java.util.Map;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;
import pf.hururaa.application.domain.Application;
import pf.hururaa.application.jpa.ApplicationRepository;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.ResolvedPathVariables;

/**
 * Resolves {@code @PathVariable} ids into {@link Application} entities, so that
 * {@code @PreAuthorize} rules can be written against the resolved application (its direction and
 * managers).
 *
 * <p>
 * An unknown (or non-numeric) id fails with {@code APPLICATION_NOT_FOUND} (see
 * {@link ResolvedPathVariables}). This is why Spring Data's auto-registered
 * {@code DomainClassConverter} isn't relied upon: it resolves an unknown id to {@code null}, which
 * the SpEL access rules can't evaluate.
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

  @Bean
  OperationCustomizer resolvedApplicationNotFoundResponse() {
    return ResolvedPathVariables.notFoundResponse(Application.class);
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
