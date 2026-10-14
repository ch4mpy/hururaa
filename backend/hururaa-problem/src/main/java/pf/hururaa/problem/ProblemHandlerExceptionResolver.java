package pf.hururaa.problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the exceptions no {@code @ExceptionHandler} handled as problems: a framework rejection
 * ({@link ErrorResponse}: unknown route, unreadable body, unsupported method...) by its status (see
 * {@link ProblemResponses#forStatus}), any other exception as a {@code 500}
 * {@link ProblemType#SERVER_ERROR} whose details are logged, never sent (SEC-6).
 *
 * <p>
 * A {@link HandlerExceptionResolver} rather than a {@code @ControllerAdvice}: those only apply to
 * annotated controllers, not to the BFF's functional routes (Spring Cloud Gateway) nor to the static
 * resources handler answering unknown routes. {@link ProblemWebMvcConfiguration} inserts it right
 * before Spring's {@code DefaultHandlerExceptionResolver}: after the {@code @ExceptionHandler}s
 * (the REST API's business problems keep their types), before the framework's {@code sendError}s.
 * </p>
 *
 * <p>
 * Left to the next resolvers, and to Spring Security's exception translation: authentication and
 * access-denied exceptions (whose {@code 401} or {@code 403} the security configuration writes),
 * clients gone away ({@link AsyncRequestNotUsableException}) and responses already committed (an
 * event stream).
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Slf4j
public class ProblemHandlerExceptionResolver implements HandlerExceptionResolver {

  private final ProblemResponses problems;

  public ProblemHandlerExceptionResolver(ProblemResponses problems) {
    this.problems = problems;
  }

  @Override
  public @Nullable ModelAndView resolveException(HttpServletRequest request,
      HttpServletResponse response, @Nullable Object handler, Exception ex) {
    if (response.isCommitted() || ex instanceof AccessDeniedException
        || ex instanceof AuthenticationException || ex instanceof AsyncRequestNotUsableException) {
      return null;
    }
    final HururaaProblemDetail problem;
    if (ex instanceof ErrorResponse errorResponse) {
      problem = ProblemResponses.forStatus(errorResponse.getStatusCode());
      errorResponse.getHeaders().forEach((name, values) -> values
          .forEach(value -> response.addHeader(name, value)));
      if (HttpStatus.valueOf(problem.getStatus()).is5xxServerError()) {
        log.error("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
      } else {
        log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
      }
    } else {
      problem = ProblemResponses.of(ProblemType.SERVER_ERROR);
      log.error("{} {}: unhandled exception", request.getMethod(), request.getRequestURI(), ex);
    }
    try {
      problems.write(request, response, problem);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new ModelAndView();
  }
}
