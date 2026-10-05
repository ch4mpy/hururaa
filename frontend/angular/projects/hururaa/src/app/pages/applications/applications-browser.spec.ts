import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { DelegationsResponse, provideApi as provideHururaaApi } from '@api/hururaa-api';
import { MessageService } from 'primeng/api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { ApplicationsBrowser } from './applications-browser';

// see PfPageStub: pf-ui can't load in jsdom, the stub mirrors pf-page's content queries
vi.mock('pf-ui', async () => ({
  PfPageComponent: (await import('../../testing/pf-page.stub')).PfPageStub,
}));

const escales = {
  id: 3,
  clientPrefix: 'escales',
  name: 'Escales',
  direction: 'dpam',
  bffClientId: 'escales-bff',
  apiClientId: 'escales-api',
};

const navire = { ...escales, id: 4, clientPrefix: 'navire', name: 'Navire' };

describe('ApplicationsBrowser', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApplicationsBrowser],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideGatewayApi('/gateway'),
        provideHururaaApi('/api'),
        provideFakeEventStreams([]),
        MessageService,
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  const render = async (delegations: Partial<DelegationsResponse>) => {
    const fixture = TestBed.createComponent(ApplicationsBrowser);
    fixture.componentRef.setInput('direction', 'dpam');
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam'] });
    TestBed.tick();
    http.expectOne('/api/me/delegations').flush({
      hururaaRoles: [],
      platformOrganization: 'dsi',
      administeredDirections: [],
      managedApplications: [],
      ...delegations,
    });
    // the resources fire their requests once the view is rendered: answer them before waiting
    // for the page to be stable
    fixture.detectChanges();
    TestBed.tick();
    http.expectOne('/api/directions/dpam').flush({ alias: 'dpam', name: 'DPAM' });
    http.match('/api/directions').forEach((r) => r.flush([]));
    const request = http.expectOne((r) => r.url === '/api/applications');
    expect(request.request.params.get('direction')).toBe('dpam');
    expect(request.request.params.get('manageable')).toBe('true');
    request.flush(
      delegations.managedApplications?.length ? delegations.managedApplications : [escales, navire],
    );
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  it("lists the direction's applications to its administrators, who register new ones", async () => {
    const page = await render({ administeredDirections: ['dpam'] });

    expect(page.querySelector('h1')?.textContent).toContain('DPAM');
    expect(page.querySelector('nav')?.textContent).toContain('Escales');
    expect(page.querySelector('nav')?.textContent).toContain('Navire');
    expect(page.textContent).toContain('Enregistrer une application');
  });

  it('lists the applications a manager manages, without registration', async () => {
    const page = await render({ managedApplications: [escales, navire] });

    expect(page.querySelector('nav')?.textContent).toContain('Navire');
    expect(page.textContent).not.toContain('Enregistrer une application');
  });

  it('leaves the list out for the manager of a single application of the direction', async () => {
    const page = await render({ managedApplications: [escales] });

    expect(page.querySelector('nav')).toBeNull();
  });
});
