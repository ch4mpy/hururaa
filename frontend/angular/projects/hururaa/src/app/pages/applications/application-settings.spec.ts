import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { ApplicationResponse, provideApi as provideHururaaApi } from '@api/hururaa-api';
import { ConfirmationService, MessageService } from 'primeng/api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { ApplicationContext } from './application-context';
import { ApplicationSettings } from './application-settings';

const hururaa: ApplicationResponse = {
  id: 1,
  clientPrefix: 'hururaa',
  name: "Hurura'a",
  direction: 'dsi',
  bffClientId: 'hururaa-bff',
  apiClientId: 'hururaa-api',
};

const teFenua: ApplicationResponse = {
  id: 2,
  clientPrefix: 'te-fenua',
  name: 'Te Fenua',
  direction: 'dsi',
  bffClientId: 'te-fenua-bff',
  apiClientId: 'te-fenua-api',
};

/** Hurura'a is renamed like any application of the DSI, but never unregistered (decision 0008). */
describe('ApplicationSettings', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApplicationSettings],
      providers: [
        ApplicationContext,
        ConfirmationService,
        MessageService,
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideGatewayApi('/gateway'),
        provideHururaaApi('/api'),
        provideFakeEventStreams([]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  const open = async (application: ApplicationResponse) => {
    TestBed.inject(ApplicationContext).bind(signal('dsi'), signal(application.id));
    const fixture = TestBed.createComponent(ApplicationSettings);
    fixture.componentRef.setInput('direction', 'dsi');
    fixture.componentRef.setInput('applicationId', application.id);
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dsi'] });
    fixture.detectChanges();
    TestBed.tick();
    http.match('/api/me/delegations').forEach((r) =>
      r.flush({
        hururaaRoles: ['hururaa.direction.admin'],
        platformOrganization: 'dsi',
        administeredDirections: ['dsi'],
        managedApplications: [],
      }),
    );
    http.expectOne(`/api/applications/${application.id}`).flush(application);
    // the application's roles are requested once it is loaded
    for (let round = 0; round < 3; round++) {
      TestBed.tick();
      http.match(() => true).forEach((r) => r.flush([]));
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it("offers to rename Hurura'a, never to unregister it", async () => {
    const page = await open(hururaa);

    expect(page.querySelector('#name')).not.toBeNull();
    expect(page.textContent).not.toContain('Désenregistrer');
  });

  it('offers to unregister any other application', async () => {
    const page = await open(teFenua);

    expect(page.textContent).toContain('Désenregistrer');
  });
});
