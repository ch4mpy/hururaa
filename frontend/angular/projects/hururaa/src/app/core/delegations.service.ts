import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { DelegationsApi, DelegationsResponse } from '@api/hururaa-api';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';
import { UserService } from './user.service';

/** Hurura'a's own roles, held in the platform organization (see the API's `HururaaPermission`). */
export const PlatformPermissions = {
  /** Register applications and set the direction managing each of them. */
  APPLICATIONS_MANAGE: 'hururaa.applications.manage',
  /** Designate the administrators of every direction. */
  DIRECTION_ADMINS_MANAGE: 'hururaa.direction-admins.manage',
} as const;

export const NO_DELEGATION: DelegationsResponse = {
  platformPermissions: [],
  platformOrganization: '',
  administeredDirections: [],
  managedApplications: [],
};

/**
 * What the current user may do in Hurura'a, level by level of the delegation chain (platform
 * administrator, direction administrator, application manager), backed by `hururaa-api`'s
 * `/me/delegations`. Menus and actions adapt to it; the API enforces the same rules anyway.
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

  readonly canManageApplications = computed(() =>
    this.delegations().platformPermissions.includes(PlatformPermissions.APPLICATIONS_MANAGE),
  );

  readonly canManageDirectionAdmins = computed(() =>
    this.delegations().platformPermissions.includes(PlatformPermissions.DIRECTION_ADMINS_MANAGE),
  );

  /** Whether the user holds any delegation at all (otherwise Hurura'a has nothing to offer). */
  readonly hasAny = computed(() => {
    const d = this.delegations();
    return (
      d.platformPermissions.length > 0 ||
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
      this.canManageDirectionAdmins() ||
      this.isDirectionAdmin(direction) ||
      this.isManagerInDirection(direction)
    );
  }
}
