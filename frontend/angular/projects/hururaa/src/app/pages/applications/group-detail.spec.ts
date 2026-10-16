import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { DelegationsResponse, provideApi as provideHururaaApi } from '@api/hururaa-api';
import { ConfirmationService, MessageService } from 'primeng/api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { ApplicationContext } from './application-context';
import { GroupDetail } from './group-detail';

const escales = {
  id: 3,
  clientPrefix: 'escales',
  name: 'Escales',
  direction: 'dpam',
  bffClientId: 'escales-bff',
  apiClientId: 'escales-api',
};

const DPAM_MANAGER: DelegationsResponse = {
  hururaaRoles: [],
  platformOrganization: 'dsi',
  administeredDirections: [],
  managedApplications: [escales],
};

const DPAM_ADMIN: DelegationsResponse = {
  hururaaRoles: [],
  platformOrganization: 'dsi',
  administeredDirections: ['dpam'],
  managedApplications: [],
};

/** One of Hurura'a's groups (decision 0008): only its members change, by whom may manage it. */
describe('GroupDetail', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GroupDetail],
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

  const open = async (group: string, delegations: DelegationsResponse) => {
    const fixture = TestBed.createComponent(GroupDetail);
    fixture.componentRef.setInput('direction', 'dpam');
    fixture.componentRef.setInput('applicationId', 1);
    fixture.componentRef.setInput('group', group);
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam'] });
    fixture.detectChanges();
    TestBed.tick();
    http.match('/api/me/delegations').forEach((r) => r.flush(delegations));
    http
      .expectOne(`/api/directions/dpam/groups/${group}`)
      .flush({ id: 'g', name: group, applicationId: 1, applicationName: "Hurura'a" });
    http
      .expectOne(`/api/directions/dpam/groups/${group}/roles`)
      .flush([
        { applicationId: 1, applicationName: "Hurura'a", clientId: 'hururaa-api', role: 'x' },
      ]);
    http
      .expectOne((r) => r.url === `/api/directions/dpam/groups/${group}/members`)
      .flush({ content: [{ id: 'a', username: 'dpam.admin' }], page: { totalElements: 1 } });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it("shows no action on the direction's administrators group to an application manager", async () => {
    const page = await open('hururaa.admins', DPAM_MANAGER);

    expect(page.textContent).toContain('porte une délégation');
    expect(page.querySelector('app-user-picker')).toBeNull();
    expect(page.querySelector('[aria-label="Retirer du groupe"]')).toBeNull();
    expect(page.textContent).not.toContain('Supprimer le groupe');
  });

  it("lets the direction's administrators change its members, never the group itself", async () => {
    const page = await open('hururaa.admins', DPAM_ADMIN);

    expect(page.querySelector('app-user-picker')).not.toBeNull();
    expect(page.querySelector('[aria-label="Retirer du groupe"]')).not.toBeNull();
    expect(page.textContent).not.toContain('Supprimer le groupe');
    expect(page.querySelector('[aria-label="Ne plus attribuer ce rôle"]')).toBeNull();
  });

  it('lets the managers of an application co-manage its managers group', async () => {
    const page = await open('hururaa.escales.product-owners', DPAM_MANAGER);

    expect(page.querySelector('app-user-picker')).not.toBeNull();
    expect(page.textContent).not.toContain('Supprimer le groupe');
  });
});
