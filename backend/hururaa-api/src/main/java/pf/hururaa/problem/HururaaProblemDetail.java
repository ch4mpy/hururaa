package pf.hururaa.problem;

import java.net.URI;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 9457 problem details whose {@code type} is one of {@link ProblemType}, plus the named values
 * the frontend interpolates in the localized message for that type.
 *
 * <p>
 * Returned as-is from {@link HururaaExceptionHandler}: Spring MVC sets the {@code instance} and
 * {@code application/problem+json} content type for any {@link ProblemDetail} body.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class HururaaProblemDetail extends ProblemDetail {
  private static final long serialVersionUID = -5170243660409538104L;

  private final Map<String, Object> parameters;

  public HururaaProblemDetail(ProblemType type, String detail, Map<String, Object> parameters) {
    super(ProblemDetail.forStatusAndDetail(type.status(), detail));
    setType(type.uri());
    this.parameters = Map.copyOf(parameters);
  }

  /** Same value as the parent's, overridden only to document it as a {@link ProblemType}. */
  @Override
  @Schema(ref = ProblemTypeOpenApiCustomizer.PROBLEM_TYPE_SCHEMA_REF)
  public URI getType() {
    return super.getType();
  }

  /** Named values to interpolate in the localized message for the {@link #getType() type}. */
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public Map<String, Object> getParameters() {
    return parameters;
  }
}
