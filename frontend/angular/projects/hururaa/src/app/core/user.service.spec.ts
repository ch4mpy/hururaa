import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '@api/gateway';
import { bffInterceptor } from './bff.interceptor';
import { LOCATION } from './location';
import { UserService } from './user.service';

describe('UserService', () => {
  let http: HttpTestingController;
  let service: UserService;
  let location: { href: string; assign: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    location = { href: 'https://host.docker.internal/fr/applications', assign: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([bffInterceptor])),
        provideHttpClientTesting(),
        provideApi('/gateway'),
        { provide: LOCATION, useValue: location },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(UserService);
  });

  afterEach(() => http.verify());

  it('is anonymous when /me answers without a subject', () => {
    http.expectOne('/gateway/me').flush({ directions: [] });

    expect(service.isAuthenticated()).toBe(false);
    expect(service.directions()).toEqual([]);
  });

  it('exposes the identity and the directions of an authenticated user', () => {
    http.expectOne('/gateway/me').flush({
      sub: 'u1',
      username: 'dpam.manager',
      firstName: 'Heimana',
      lastName: 'Teuira',
      directions: ['dpam'],
    });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.displayName()).toBe('Heimana Teuira');
    expect(service.directions()).toEqual(['dpam']);
  });

  it('is anonymous when /me fails', () => {
    http.expectOne('/gateway/me').flush(null, { status: 502, statusText: 'Bad Gateway' });

    expect(service.isAuthenticated()).toBe(false);
  });

  it('logs in with a real navigation, coming back to the current page', () => {
    http.expectOne('/gateway/me').flush({ directions: [] });

    service.login();

    const request = http.expectOne('/gateway/oauth2/authorization/hururaa-bff');
    expect(request.request.headers.get('X-POST-LOGIN-SUCCESS-URI')).toBe(location.href);
    expect(request.request.withCredentials).toBe(true);
    request.flush(null, { headers: { Location: 'https://host.docker.internal/auth/realms/x' } });
    expect(location.assign).toHaveBeenCalledWith('https://host.docker.internal/auth/realms/x');
  });
});
