/**
 * RFC 9457 problem details with a closed set of {@link pf.hururaa.problem.ProblemType types}, so
 * that the frontend can localize errors from the generated client's enum instead of displaying
 * server-side messages, and the components writing every error of the REST API and the BFF as one
 * of them (docs/intent/0003): {@link pf.hururaa.problem.ProblemAuthenticationEntryPoint},
 * {@link pf.hururaa.problem.ProblemAccessDeniedHandler}, wired by each application's security
 * configuration, and, picked up by the component scan of the applications rooted in
 * {@code pf.hururaa}, {@link pf.hururaa.problem.ProblemHandlerExceptionResolver} (the exceptions no
 * {@code @ExceptionHandler} handled) and {@link pf.hururaa.problem.HururaaErrorController} (what
 * still reaches {@code /error}).
 */
@NullMarked
package pf.hururaa.problem;

import org.jspecify.annotations.NullMarked;
