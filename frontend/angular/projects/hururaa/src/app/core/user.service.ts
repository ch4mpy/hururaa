import { HttpContext, HttpResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { GatewayApi, UserResponse } from '@api/gateway';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';
import { POST_LOGIN_SUCCESS_URI, POST_LOGOUT_SUCCESS_URI } from './bff.interceptor';
import { LOCATION } from './location';

/** What the gateway's `/me` returns when there is no session. */
export const ANONYMOUS: UserResponse = { directions: [] };

/**
 * Current user state, backed by the gateway's `/me` endpoint (which always answers `200 OK`, with
 * empty values when unauthenticated): identity and the directions the user is a member of. What
 * the user may do in Hurura'a comes from `hururaa-api` instead (see `DelegationsService`).
 *
 * Login and logout are real navigations: the gateway answers with the URI to follow in a
 * `Location` header and the browser is sent there (an XHR could not follow the redirect chain
 * through the authorization server).
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly gateway = inject(GatewayApi);
  private readonly location = inject(LOCATION);

  private readonly user = signal<UserResponse>(ANONYMOUS);
  private loading$: Observable<UserResponse>;

  /** The current user: `ANONYMOUS` until `/me` answers, or when there is no session. */
  readonly current = this.user.asReadonly();

  readonly isAuthenticated = computed(() => !!this.user().sub);

  /** Full name if known, falling back to the username, then to the e-mail. */
  readonly displayName = computed(() => {
    const { firstName, lastName, username, email } = this.user();
    const fullName = [firstName, lastName].filter((s) => !!s).join(' ');
    // || rather than ??: an empty (not only missing) full name falls through to the next one
    // eslint-disable-next-line @typescript-eslint/prefer-nullish-coalescing
    return fullName || username || email || '';
  });

  /** The directions (Keycloak organizations) the user is a member of. */
  readonly directions = computed(() => this.user().directions ?? []);

  constructor() {
    this.loading$ = this.refresh();
  }

  /** Re-fetches `/me`. The returned observable replays the result to late subscribers. */
  refresh(): Observable<UserResponse> {
    this.loading$ = this.gateway.getMe().pipe(
      catchError(() => of(ANONYMOUS)),
      tap((user) => this.user.set(user)),
      shareReplay(1),
    );
    // shareReplay is lazy: fire the request right away rather than on the first whenLoaded() call
    this.loading$.subscribe();
    return this.loading$;
  }

  /** Emits the current user once the latest `/me` request has completed (for route guards). */
  whenLoaded(): Observable<UserResponse> {
    return this.loading$;
  }

  /** Starts the authorization code flow, coming back to the current page once logged in. */
  login(): void {
    const context = new HttpContext().set(POST_LOGIN_SUCCESS_URI, this.location.href);
    this.gateway
      .startLoginWithHururaabff('response', false, { context })
      .subscribe((response) => this.follow(response));
  }

  /** Terminates the session (BFF and authorization server), coming back to the current page. */
  logout(): void {
    const context = new HttpContext().set(POST_LOGOUT_SUCCESS_URI, this.location.href);
    this.gateway.logout('response', false, { context }).subscribe((response) => {
      this.user.set(ANONYMOUS);
      this.follow(response);
    });
  }

  /** Navigates to the `Location` header of the response. */
  private follow(response: HttpResponse<unknown>): void {
    const target = response.headers.get('Location');
    if (target) {
      this.location.assign(new URL(target, this.location.href).href);
    }
  }
}
