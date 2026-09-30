import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ErrorBannerService } from './error-banner.service';
import { problemMessage } from './problem-messages';

/**
 * Set on a request whose failures are handled by the caller (inline, in a dialog...) so that the
 * interceptor does not also show them in the shell's error banner.
 */
export const SKIP_ERROR_BANNER = new HttpContextToken<boolean>(() => false);

/**
 * The localized message for a failed HTTP call: the one matching the API's typed problem when the
 * body is one (see `problemMessage`), otherwise a generic message for the status class.
 */
export function httpErrorMessage(error: HttpErrorResponse): string {
  const message = problemMessage(error);
  if (message) {
    return message;
  }
  if (error.status === 0) {
    return $localize`:@@http.error.network:Serveur injoignable, vérifiez votre connexion`;
  }
  if (error.status === 401) {
    return $localize`:@@http.error.unauthorized:Session expirée, veuillez vous reconnecter`;
  }
  if (error.status === 403) {
    return $localize`:@@http.error.forbidden:Accès refusé`;
  }
  if (error.status === 404) {
    return $localize`:@@http.error.notFound:Ressource introuvable`;
  }
  if (error.status >= 500) {
    return $localize`:@@http.error.server:Erreur du serveur, veuillez réessayer plus tard`;
  }
  return $localize`:@@http.error.client:Requête refusée par le serveur`;
}

/**
 * Shows every failed HTTP call (network failure, `4xx` or `5xx`) in the shell's error banner, with
 * the localized message for the API's typed problem when the response carries one.
 *
 * The error is re-thrown untouched: callers still get it (to reset a "saving" flag, keep a form
 * editable...) and can opt out of the banner with `SKIP_ERROR_BANNER` when they display the
 * failure themselves.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const banner = inject(ErrorBannerService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.context.get(SKIP_ERROR_BANNER)) {
        banner.show(httpErrorMessage(error));
      }
      return throwError(() => error);
    }),
  );
};
