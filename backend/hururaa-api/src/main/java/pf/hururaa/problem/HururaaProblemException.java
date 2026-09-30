package pf.hururaa.problem;

import java.util.Map;
import lombok.Getter;

/**
 * Thrown by business rules; carries the exact {@link HururaaProblemDetail} the client gets (see
 * {@link HururaaExceptionHandler}).
 *
 * <p>
 * Checked, so that endpoint signatures list the problems they can actually report. Two
 * consequences: {@code @Transactional} methods that can throw it and write must declare
 * {@code rollbackFor = HururaaProblemException.class} (Spring only rolls back on unchecked
 * exceptions by default), and code that can't declare it (Spring {@code Converter}s, lambdas)
 * throws it wrapped in an {@link HururaaProblemRuntimeException}.
 * </p>
 *
 * <p>
 * Deliberately not an {@link org.springframework.web.ErrorResponseException}: Spring Boot's
 * {@code ProblemDetailsExceptionHandler} would compete for it.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Getter
public class HururaaProblemException extends Exception {
  private static final long serialVersionUID = 2596330081420287571L;

  private final ProblemType type;

  private final transient HururaaProblemDetail problem;

  /**
   * @param type the problem type
   * @param detail a human-readable, non-localized explanation (for logs and non-browser clients)
   * @param parameters the values listed on the {@link ProblemType} constant
   */
  public HururaaProblemException(ProblemType type, String detail, Map<String, Object> parameters) {
    super(detail);
    this.type = type;
    this.problem = new HururaaProblemDetail(type, detail, parameters);
  }

  /** For the places that can't declare a checked exception. */
  public HururaaProblemRuntimeException unchecked() {
    return new HururaaProblemRuntimeException(this);
  }
}
