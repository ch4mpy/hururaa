import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import {
  ApplicationResponse,
  ApplicationsApi,
  DelegationsApi,
  DelegationsResponse,
} from '@api/hururaa-api';
import { Observable, catchError, filter, map, of, shareReplay, switchMap, take, tap } from 'rxjs';
import { UserService } from './user.service';

/** Hurura'a's own roles, as listed in `hururaaRoles` when held in the DSI (see the API's `HururaaPermission`). */
export const HururaaRoles = {
  /** Administrator of the DSI, hence Hurura'a administrator: act at every level of Hurura'a. */
  ADMIN: 'hururaa.direction.admin',
} as const;

/** The client prefix of the Hurura'a application, available in every direction (decision 0008). */
export const HURURAA_CLIENT_PREFIX = 'hururaa';

/** What the names of Hurura'a's groups start with. */
const RESERVED_PREFIX = `${HURURAA_CLIENT_PREFIX}.`;

const PRODUCT_OWNERS_SUFFIX = '.product-owners';

/** The name of a direction's administrators' group, one of Hurura'a's groups. */
export const ADMINS_GROUP = `${RESERVED_PREFIX}admins`;

/** The group of an application's managers, one of Hurura'a's groups in the application's direction. */
export function productOwnersGroup(clientPrefix: string): string {
  return `${RESERVED_PREFIX}${clientPrefix}${PRODUCT_OWNERS_SUFFIX}`;
}

/**
 * The client prefix of the application whose managers a group holds (`escales` for
 * `hururaa.escales.product-owners`), `undefined` for any other group: mirrors the API's
 * `DelegationGroups.productOwnersPrefix`.
 */
export function productOwnersPrefix(name: string): string | undefined {
  if (!name.startsWith(RESERVED_PREFIX) || !name.endsWith(PRODUCT_OWNERS_SUFFIX)) {
    return undefined;
  }
  const prefix = name.slice(RESERVED_PREFIX.length, -PRODUCT_OWNERS_SUFFIX.length);
  return prefix && !prefix.includes('.') ? prefix : undefined;
}

/** Whether a group is one of Hurura'a's, carrying a delegation (names starting with `hururaa.`). */
export function isReservedGroup(name: string): boolean {
  return name.startsWith(RESERVED_PREFIX);
}

export const NO_DELEGATION: DelegationsResponse = {
  hururaaRoles: [],
  platformOrganization: '',
  administeredDirections: [],
  managedApplications: [],
};

/**
 * What the current user may do in Hurura'a, level by level of the delegation chain (Hurura'a
 * administrator, direction administrator, application manager), backed by `hururaa-api`'s
 * `/me/delegations`, which reads them from the user's token: a delegation granted or revoked shows
 * once the token is renewed. Menus and actions adapt to it, mirroring the API's access rules, which the API
 * enforces anyway.
 *
 * Refetched whenever the user logs in or out, and on demand (`refresh()`) after a change that may
 * affect the current user's own delegations.
 */
@Injectable({ providedIn: 'root' })
export class DelegationsService {
  private readonly api = inject(DelegationsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private hururaa$?: Observable<ApplicationResponse | undefined>;
  private readonly user = inject(UserService);

  private readonly delegations = signal<DelegationsResponse>(NO_DELEGATION);

  /** The user (`sub`, `''` when anonymous) the current delegations were fetched for. */
  private readonly loadedFor = signal<string | undefined>(undefined);
  private readonly loadedFor$ = toObservable(this.loadedFor);

  readonly current = this.delegations.asReadonly();

  /**
   * Whether the user is a Hurura'a administrator: they act at every level, and alone designate
   * direction administrators.
   */
  readonly isAdmin = computed(() => this.delegations().hururaaRoles.includes(HururaaRoles.ADMIN));

  /**
   * Whether the user manages Hurura'a: a delegate of every direction, managing the members of its
   * groups (administrators and managers) everywhere.
   */
  readonly isHururaaManager = computed(() =>
    this.delegations().managedApplications.some((a) => a.clientPrefix === HURURAA_CLIENT_PREFIX),
  );

  /**
   * The aliases of the directions the user has a say on, which they administer or manage
   * applications of (`undefined`: all of them, for a Hurura'a administrator or manager).
   */
  readonly directions = computed(() => {
    if (this.isAdmin() || this.isHururaaManager()) {
      return undefined;
    }
    const { administeredDirections, managedApplications } = this.delegations();
    return [
      ...new Set([...administeredDirections, ...managedApplications.map((a) => a.direction)]),
    ];
  });

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
        this.loadedFor.set('');
      }
    });
  }

  refresh(): Observable<DelegationsResponse> {
    const sub = this.user.current().sub ?? '';
    const loading$ = this.api.getMyDelegations().pipe(
      catchError(() => of(NO_DELEGATION)),
      tap((delegations) => {
        this.delegations.set(delegations);
        this.loadedFor.set(sub);
      }),
      shareReplay(1),
    );
    loading$.subscribe();
    return loading$;
  }

  /**
   * Emits the delegations once those of the current user (as known once `/me` answered) are
   * loaded: for route guards.
   */
  whenLoaded(): Observable<DelegationsResponse> {
    return this.user.whenLoaded().pipe(
      switchMap((user) =>
        this.loadedFor$.pipe(
          filter((sub) => sub === (user.sub ?? '')),
          take(1),
        ),
      ),
      map(() => this.delegations()),
    );
  }

  /** Those of the directions the user has a say on. */
  withSay<T extends { alias: string }>(directions: T[]): T[] {
    const mine = this.directions();
    return mine ? directions.filter((d) => mine.includes(d.alias)) : directions;
  }

  isDirectionAdmin(direction: string): boolean {
    return this.delegations().administeredDirections.includes(direction);
  }

  isApplicationManager(applicationId: number): boolean {
    return this.delegations().managedApplications.some((a) => a.id === applicationId);
  }

  /** The applications of the direction the user manages (as a manager, not as an administrator). */
  managedApplicationsIn(direction: string) {
    return this.delegations().managedApplications.filter((a) => a.direction === direction);
  }

  /** Whether the user has a say on the direction (what the API requires to read its groups & co). */
  canReadDirection(direction: string): boolean {
    return (
      this.isAdmin() ||
      this.isHururaaManager() ||
      this.isDirectionAdmin(direction) ||
      this.managedApplicationsIn(direction).length > 0
    );
  }

  /**
   * Whether the user administers the direction: registers, renames or unregisters its
   * applications, and manages all of them.
   */
  canEditApplicationsOf(direction: string): boolean {
    return this.isAdmin() || this.isDirectionAdmin(direction);
  }

  /**
   * Whether the user may read and define an application's roles, groups and managers. Hurura'a,
   * whose groups live in every direction, is read by any delegate of the direction it is opened in.
   */
  canManageApplication(application: {
    id: number;
    direction: string;
    clientPrefix?: string;
  }): boolean {
    if (application.clientPrefix === HURURAA_CLIENT_PREFIX) {
      return this.canReadDirection(application.direction);
    }
    return (
      this.canEditApplicationsOf(application.direction) || this.isApplicationManager(application.id)
    );
  }

  /**
   * Whether the user may change a group's members (and, for a group which is not one of
   * Hurura'a's, its roles): mirrors the API's `DelegatedGroup.isManageableBy` — the direction's
   * administrators, the managers of the group's application, and, for
   * `hururaa.<prefix>.product-owners`, the managers of that application.
   */
  canManageGroup(group: { name: string; applicationId?: number }, direction: string): boolean {
    const coManagedPrefix = productOwnersPrefix(group.name);
    // a group of Hurura'a which carries no delegation it knows of: the API refuses any change
    if (isReservedGroup(group.name) && group.name !== ADMINS_GROUP && !coManagedPrefix) {
      return false;
    }
    if (this.canEditApplicationsOf(direction)) {
      return true;
    }
    if (group.applicationId !== undefined && this.isApplicationManager(group.applicationId)) {
      return true;
    }
    return (
      !!coManagedPrefix &&
      this.managedApplicationsIn(direction).some((a) => a.clientPrefix === coManagedPrefix)
    );
  }

  /**
   * Hurura'a, the only application available in every direction (decision 0008), whose id the
   * links to its groups need: read once.
   */
  hururaaApplication(): Observable<ApplicationResponse | undefined> {
    this.hururaa$ ??= this.applicationsApi.getApplications().pipe(
      map((applications) => applications.find((a) => a.clientPrefix === HURURAA_CLIENT_PREFIX)),
      shareReplay(1),
    );
    return this.hururaa$;
  }
}
