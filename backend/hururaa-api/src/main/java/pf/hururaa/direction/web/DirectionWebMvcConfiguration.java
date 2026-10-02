package pf.hururaa.direction.web;

import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;
import pf.hururaa.direction.DelegationResolver;
import pf.hururaa.direction.domain.DelegatedDirection;
import pf.hururaa.direction.domain.DelegatedGroup;
import pf.hururaa.problem.HururaaProblemException;
import pf.hururaa.problem.ResolvedPathVariables;

/**
 * Resolves the {@code {direction}} and {@code {group}} path variables into a
 * {@link DelegatedDirection} and a {@link DelegatedGroup}, so that {@code @PreAuthorize} rules can
 * be written against their delegations. An unknown direction or group fails with
 * {@code DIRECTION_NOT_FOUND} or {@code GROUP_NOT_FOUND} (see {@link ResolvedPathVariables}).
 *
 * <p>
 * A group's name is unique only within its direction: its converter reads the direction alias from
 * the {@value DirectionController#DIRECTION_PLACEHOLDER} variable of the request's path.
 * </p>
 *
 * <p>
 * The resolver is looked up lazily: every {@code WebMvcConfigurer} is part of any
 * {@code @WebMvcTest} slice, including those of controllers that neither need nor mock it.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@RequiredArgsConstructor
public class DirectionWebMvcConfiguration implements WebMvcConfigurer {

  private final ObjectProvider<DelegationResolver> resolver;

  @Bean
  OperationCustomizer resolvedDirectionOrGroupNotFoundResponse() {
    return ResolvedPathVariables.notFoundResponse(DelegatedDirection.class, DelegatedGroup.class);
  }

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new DirectionConverter(resolver));
    registry.addConverter(new GroupConverter(resolver));
  }

  @RequiredArgsConstructor
  static class DirectionConverter implements Converter<String, DelegatedDirection> {
    private final ObjectProvider<DelegationResolver> resolver;

    @Override
    public DelegatedDirection convert(String alias) {
      try {
        return resolver.getObject().direction(alias);
      } catch (HururaaProblemException e) {
        throw e.unchecked();
      }
    }
  }

  @RequiredArgsConstructor
  static class GroupConverter implements Converter<String, DelegatedGroup> {
    private final ObjectProvider<DelegationResolver> resolver;

    @Override
    public DelegatedGroup convert(String name) {
      try {
        return resolver.getObject().group(currentDirectionAlias(), name);
      } catch (HururaaProblemException e) {
        throw e.unchecked();
      }
    }

    @SuppressWarnings("unchecked")
    private static String currentDirectionAlias() {
      final var pathVariables = (Map<String, String>) RequestContextHolder
          .currentRequestAttributes()
          .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
              RequestAttributes.SCOPE_REQUEST);
      final var alias = pathVariables == null ? null
          : pathVariables.get(DirectionController.DIRECTION_PLACEHOLDER);
      if (alias == null) {
        throw new IllegalStateException("A group is resolved only under a {"
            + DirectionController.DIRECTION_PLACEHOLDER + "} path variable");
      }
      return alias;
    }
  }
}
