import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SESSION_PROBE } from './http-error.interceptor';
import { UserService } from './user.service';

/**
 * Switches the shell to the logged-out state when a call answers `401`: the gateway session is gone
 * (expired, gateway restarted...), so the user is re-fetched from `/me`, which then answers
 * anonymous and the header, menus and delegations follow. The error banner still shows "session
 * expired" (`httpErrorInterceptor`), and the error is re-thrown untouched.
 *
 * The session probe itself (`/me`) is ignored: its `401` already means anonymous, and re-fetching
 * it would loop. `UserService` is looked up only when a `401` comes back: injecting it up front
 * would be circular, since its own constructor sends `/me` through this interceptor.
 */
export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.context.get(SESSION_PROBE)
      ) {
        injector.get(UserService).sessionExpired();
      }
      return throwError(() => error);
    }),
  );
};
