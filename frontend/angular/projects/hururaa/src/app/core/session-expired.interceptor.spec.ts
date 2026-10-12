import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '@api/gateway';
import { firstValueFrom } from 'rxjs';
import { SESSION_PROBE } from './http-error.interceptor';
import { LOCATION } from './location';
import { sessionExpiredInterceptor } from './session-expired.interceptor';
import { UserService } from './user.service';

describe('sessionExpiredInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let user: UserService;

  const authenticated = { sub: 'u1', username: 'dpam.manager', directions: ['dpam'] };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
        provideHttpClientTesting(),
        provideApi('/gateway'),
        {
          provide: LOCATION,
          useValue: { href: 'https://host.docker.internal/fr/', assign: vi.fn() },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    user = TestBed.inject(UserService);
    backend.expectOne('/gateway/me').flush(authenticated);
  });

  afterEach(() => backend.verify());

  it('switches to the logged-out state when a call answers 401', async () => {
    expect(user.isAuthenticated()).toBe(true);

    const response = firstValueFrom(http.get('/gateway/bff/api/v1/me/delegations'));
    backend
      .expectOne('/gateway/bff/api/v1/me/delegations')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await expect(response).rejects.toBeInstanceOf(HttpErrorResponse);

    backend.expectOne('/gateway/me').flush({ directions: [] });
    expect(user.isAuthenticated()).toBe(false);
  });

  it('does not re-fetch the user for the session probe, whose 401 already means anonymous', async () => {
    const context = new HttpContext().set(SESSION_PROBE, true);
    const response = firstValueFrom(http.get('/gateway/me', { context }));
    backend.expectOne('/gateway/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await expect(response).rejects.toBeInstanceOf(HttpErrorResponse);

    backend.expectNone('/gateway/me');
  });

  it('leaves the user alone on other errors', async () => {
    const response = firstValueFrom(http.get('/gateway/bff/api/v1/me/delegations'));
    backend
      .expectOne('/gateway/bff/api/v1/me/delegations')
      .flush(null, { status: 403, statusText: 'Forbidden' });
    await expect(response).rejects.toBeInstanceOf(HttpErrorResponse);

    backend.expectNone('/gateway/me');
    expect(user.isAuthenticated()).toBe(true);
  });
});
