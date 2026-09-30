import { HttpClient, HttpContext, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HururaaProblemDetail, ProblemType } from '@api/hururaa-api';
import { firstValueFrom } from 'rxjs';
import { ErrorBannerService } from './error-banner.service';
import { SKIP_ERROR_BANNER, httpErrorInterceptor, httpErrorMessage } from './http-error.interceptor';

describe('httpErrorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let banner: ErrorBannerService;

  const applicationNotFound: HururaaProblemDetail = {
    type: ProblemType.APPLICATION_NOT_FOUND,
    title: 'Not Found',
    status: 404,
    detail: 'No application with id 42',
    instance: '/applications/42',
    parameters: { applicationId: 42 },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    banner = TestBed.inject(ErrorBannerService);
  });

  afterEach(() => backend.verify());

  it('shows the localized message of a typed problem and re-throws the error', async () => {
    const response = firstValueFrom(http.get('/api/applications/42'));
    backend.expectOne('/api/applications/42').flush(applicationNotFound, { status: 404, statusText: 'Not Found' });

    await expect(response).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(banner.message()).toBe('Application n° 42 introuvable');
  });

  it('falls back to a generic message when the body is not a typed problem', async () => {
    const response = firstValueFrom(http.get('/api/forms'));
    backend.expectOne('/api/forms').flush('boom', { status: 500, statusText: 'Internal Server Error' });

    await expect(response).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(banner.message()).toBe('Erreur du serveur, veuillez réessayer plus tard');
  });

  it('leaves the banner alone on success', async () => {
    const response = firstValueFrom(http.get('/api/forms'));
    backend.expectOne('/api/forms').flush([]);

    await expect(response).resolves.toEqual([]);
    expect(banner.message()).toBeUndefined();
  });

  it('leaves the banner alone when the caller opted out', async () => {
    const context = new HttpContext().set(SKIP_ERROR_BANNER, true);
    const response = firstValueFrom(http.get('/api/applications/42', { context }));
    backend.expectOne('/api/applications/42').flush(applicationNotFound, { status: 404, statusText: 'Not Found' });

    await expect(response).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(banner.message()).toBeUndefined();
  });

  describe('httpErrorMessage', () => {
    const messageFor = (status: number) => httpErrorMessage(new HttpErrorResponse({ status }));

    it('has a generic message per status class', () => {
      expect(messageFor(0)).toBe('Serveur injoignable, vérifiez votre connexion');
      expect(messageFor(401)).toBe('Session expirée, veuillez vous reconnecter');
      expect(messageFor(403)).toBe('Accès refusé');
      expect(messageFor(404)).toBe('Ressource introuvable');
      expect(messageFor(409)).toBe('Requête refusée par le serveur');
      expect(messageFor(503)).toBe('Erreur du serveur, veuillez réessayer plus tard');
    });

    it('prefers the typed problem message over the status one', () => {
      expect(httpErrorMessage(new HttpErrorResponse({ status: 404, error: applicationNotFound }))).toBe(
        'Application n° 42 introuvable',
      );
    });
  });
});
