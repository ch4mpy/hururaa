import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { DelegationsResponse, provideApi as provideHururaaApi } from '@api/hururaa-api';
import { Observable, firstValueFrom } from 'rxjs';
import { HururaaRoles } from './delegations.service';
import { skipApplicationChoice, skipDirectionChoice } from './landing.guards';

const application = (id: number, direction: string) => ({
  id,
  clientPrefix: `app${id}`,
  name: `App ${id}`,
  direction,
  bffClientId: `app${id}-bff`,
  apiClientId: `app${id}-api`,
});

const delegations = (d: Partial<DelegationsResponse>): DelegationsResponse => ({
  hururaaRoles: [],
  platformOrganization: 'dsi',
  administeredDirections: [],
  managedApplications: [],
  ...d,
});

describe('landing guards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideGatewayApi('/gateway'),
        provideHururaaApi('/api'),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  /** Runs the guard for a user with these delegations, returns the URL it redirects to, if any. */
  const run = async (
    guard: typeof skipApplicationChoice,
    mine: DelegationsResponse,
    params: Record<string, string> = {},
  ): Promise<string | undefined> => {
    const route = { pathFromRoot: [{ paramMap: new Map(Object.entries(params)) }] };
    const result = TestBed.runInInjectionContext(
      () =>
        guard(route as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as Observable<
          boolean | UrlTree
        >,
    );
    const pending = firstValueFrom(result);
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam', 'daf'] });
    TestBed.tick();
    http.expectOne('/api/me/delegations').flush(mine);
    TestBed.tick();
    const outcome = await pending;
    return outcome instanceof UrlTree ? TestBed.inject(Router).serializeUrl(outcome) : undefined;
  };

  it('skips the direction choice of a user with a say on a single direction', async () => {
    const mine = delegations({
      managedApplications: [application(3, 'dpam'), application(4, 'dpam')],
    });

    expect(await run(skipDirectionChoice('users'), mine)).toBe('/users/dpam');
  });

  it('lets users with a say on several directions choose', async () => {
    const mine = delegations({
      administeredDirections: ['daf'],
      managedApplications: [application(3, 'dpam')],
    });

    expect(await run(skipDirectionChoice('applications'), mine)).toBeUndefined();
  });

  it("always lets Hurura'a administrators choose (they create directions there)", async () => {
    const mine = delegations({ hururaaRoles: [HururaaRoles.ADMIN] });

    expect(await run(skipDirectionChoice('applications'), mine)).toBeUndefined();
  });

  it('opens the application of a manager of a single one in the direction', async () => {
    const mine = delegations({
      managedApplications: [application(3, 'dpam'), application(5, 'daf')],
    });

    expect(await run(skipApplicationChoice, mine, { direction: 'dpam' })).toBe(
      '/applications/dpam/3',
    );
  });

  it('shows the direction to its administrators, even managing one of its applications', async () => {
    const mine = delegations({
      administeredDirections: ['dpam'],
      managedApplications: [application(3, 'dpam')],
    });

    expect(await run(skipApplicationChoice, mine, { direction: 'dpam' })).toBeUndefined();
  });
});
