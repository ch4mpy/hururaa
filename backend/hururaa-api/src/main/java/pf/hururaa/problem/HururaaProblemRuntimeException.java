package pf.hururaa.problem;

/**
 * Wraps a {@link HururaaProblemException} where a checked exception can't be declared (Spring
 * {@code Converter}s, lambdas). {@link HururaaExceptionHandler} resolves handlers through the
 * cause chain, so the wrapped problem is reported as if thrown directly.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class HururaaProblemRuntimeException extends RuntimeException {
  private static final long serialVersionUID = -1234860742883560713L;

  public HururaaProblemRuntimeException(HururaaProblemException cause) {
    super(cause.getMessage(), cause);
  }

  @Override
  public synchronized HururaaProblemException getCause() {
    return (HururaaProblemException) super.getCause();
  }
}
