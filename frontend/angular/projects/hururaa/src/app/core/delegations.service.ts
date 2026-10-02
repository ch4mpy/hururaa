import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { DelegationsApi, DelegationsResponse } from '@api/hururaa-api';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';
import { UserService } from './user.service';

/** Hurura'a's own roles, held in the DSI (see the API's `HururaaPermission`). */
export const HururaaRoles = {
  /** Act at every level of Hurura'a. */
  ADMIN: 'hururaa.admin',
} as const;

export const NO_DELEGATION: DelegationsResponse = {
  hururaaRoles: [],
  platformOrganization: '',
  administeredDirections: [],
  managedApplications: [],
};

/**
 * What the current user may do in Hurura'a, level by level of the delegation chain (Hurura'a
 * administrator, direction administrator, application manager), backed by `hururaa-api`'s
 * `/me/delegations`. Menus and actions adapt to it, mirroring the API's access rules, which the API
 * enforces anyway.
 *
 * Refetched whenever the user logs in or out, and on demand (`refresh()`) after a change that may
 * affect the current user's own delegations.
 */
@Injectable({ providedIn: 'root' })
export class DelegationsService {
  private readonly api = inject(DelegationsApi);
  private readonly user = inject(UserService);

  private readonly delegations = signal<DelegationsResponse>(NO_DELEGATION);
  private loading$: Observable<DelegationsResponse> = of(NO_DELEGATION);

  readonly current = this.delegations.asReadonly();

  /**
   * Whether the user is a Hurura'a administrator: they act at every level, and alone designate
   * direction administrators and move applications between directions.
   */
  readonly isAdmin = computed(() => this.delegations().hururaaRoles.includes(HururaaRoles.ADMIN));

  /** The directions the user may register applications in (`undefined`: all of them). */
  readonly registrationDirections = computed(() =>
    this.isAdmin() ? undefined : this.delegations().administeredDirections,
  );

  /** Whether the user holds any delegation at all (otherwise Hurura'a has nothing to offer). */
  readonly hasAny = computed(() => {
    const d = this.delegations();
    return (
      d.hururaaRoles.length > 0 ||
      d.administeredDirections.length > 0 ||
      d.managedApplications.length > 0
    );
  });

  constructor() {
    effect(() => {
      if (this.user.isAuthenticated()) {
        this.refresh();
      } else {
        this.delegations.set(NO_DELEGATION);
        this.loading$ = of(NO_DELEGATION);
      }
    });
  }

  refresh(): Observable<DelegationsResponse> {
    this.loading$ = this.api.getMyDelegations().pipe(
      catchError(() => of(NO_DELEGATION)),
      tap((delegations) => this.delegations.set(delegations)),
      shareReplay(1),
    );
    this.loading$.subscribe();
    return this.loading$;
  }

  isDirectionAdmin(direction: string): boolean {
    return this.delegations().administeredDirections.includes(direction);
  }

  isApplicationManager(applicationId: number): boolean {
    return this.delegations().managedApplications.some((a) => a.id === applicationId);
  }

  /** Whether the user manages at least one of the direction's applications. */
  isManagerInDirection(direction: string): boolean {
    return this.delegations().managedApplications.some((a) => a.direction === direction);
  }

  /** Whether the user has a say on the direction (what the API requires to read its groups & co). */
  canReadDirection(direction: string): boolean {
    return (
      this.isAdmin() || this.isDirectionAdmin(direction) || this.isManagerInDirection(direction)
    );
  }

  /** Whether the user may rename or unregister the direction's applications. */
  canEditApplicationsOf(direction: string): boolean {
    return this.isAdmin() || this.isDirectionAdmin(direction);
  }

  /** Whether the user may read and define an application's roles and managers. */
  canManageApplication(application: { id: number; direction: string }): boolean {
    return (
      this.canEditApplicationsOf(application.direction) || this.isApplicationManager(application.id)
    );
  }

  /** Whether the user may create groups in the direction. */
  canCreateGroupsIn(direction: string): boolean {
    return (
      this.isAdmin() || this.isDirectionAdmin(direction) || this.isManagerInDirection(direction)
    );
  }

  /** Whether the user may grant the application's roles through groups. */
  canGrantRolesOf(application: { id: number; direction: string }): boolean {
    return this.canManageApplication(application);
  }
}
