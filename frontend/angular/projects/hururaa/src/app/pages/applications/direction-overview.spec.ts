import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { provideApi as provideHururaaApi } from '@api/hururaa-api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { DirectionOverview } from './direction-overview';

const escales = {
  id: 3,
  clientPrefix: 'escales',
  name: 'Escales',
  direction: 'dpam',
  bffClientId: 'escales-bff',
  apiClientId: 'escales-api',
};

const hururaa = {
  id: 1,
  clientPrefix: 'hururaa',
  name: "Hurura'a",
  direction: 'dsi',
  bffClientId: 'hururaa-bff',
  apiClientId: 'hururaa-api',
};

/** The direction's administrators are designated in Hurura'a's `hururaa.admins` group. */
describe('DirectionOverview', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DirectionOverview],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideGatewayApi('/gateway'),
        provideHururaaApi('/api'),
        provideFakeEventStreams([]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it("lists the administrators to the direction's delegates, with a link to their group", async () => {
    const fixture = TestBed.createComponent(DirectionOverview);
    fixture.componentRef.setInput('direction', 'dpam');
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam'] });
    fixture.detectChanges();
    TestBed.tick();
    http.match('/api/me/delegations').forEach((r) =>
      r.flush({
        hururaaRoles: [],
        platformOrganization: 'dsi',
        administeredDirections: [],
        managedApplications: [escales],
      }),
    );
    TestBed.tick();
    http
      .expectOne('/api/directions/dpam/admins')
      .flush([{ id: 'a', username: 'dpam.admin', firstName: 'Admin', lastName: 'Dpam' }]);
    http.expectOne((r) => r.url === '/api/applications').flush([escales, hururaa]);
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Admin Dpam');
    const link = page.querySelector('a')!;
    expect(link.getAttribute('href')).toBe('/applications/dpam/1/groups/hururaa.admins');
    expect(page.querySelector('app-user-picker')).toBeNull();
  });
});
