import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { provideApi as provideHururaaApi } from '@api/hururaa-api';
import { MessageService } from 'primeng/api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { Users } from './users';

// see PfPageStub: pf-ui can't load in jsdom, the stub mirrors pf-page's content queries
vi.mock('pf-ui', async () => ({
  PfPageComponent: (await import('../../testing/pf-page.stub')).PfPageStub,
}));

describe('Users', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Users],
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

  it("searches the direction's members, narrowed by the chosen filters", async () => {
    const fixture = TestBed.createComponent(Users);
    fixture.componentRef.setInput('direction', 'dpam');
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam'] });
    fixture.detectChanges();
    TestBed.tick();
    http.match('/api/me/delegations').forEach((r) =>
      r.flush({
        hururaaRoles: [],
        platformOrganization: 'dsi',
        administeredDirections: ['dpam'],
        managedApplications: [],
      }),
    );
    http.match('/api/directions').forEach((r) => r.flush([]));
    http.expectOne((r) => r.url === '/api/applications').flush([]);
    http.expectOne('/api/directions/dpam/groups').flush([]);
    http
      .expectOne((r) => r.url === '/api/directions/dpam/users')
      .flush({
        content: [{ id: 'a', username: 'dpam.agent', firstName: 'Agent', lastName: 'Dpam' }],
        page: { totalElements: 1 },
      });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Agent Dpam');

    // the component's own filter setters, as the selects call them
    const users = fixture.componentInstance as unknown as {
      selectApplication(id: number): void;
      narrow(filter: unknown, value: string): void;
      role: unknown;
    };
    users.selectApplication(3);
    users.narrow(users.role, 'escales.read');
    TestBed.tick();
    http.expectOne((r) => r.url === '/api/directions/dpam/applications/3/roles').flush([]);
    const search = http.expectOne((r) => r.url === '/api/directions/dpam/users');
    expect(search.request.params.get('applicationId')).toBe('3');
    expect(search.request.params.get('role')).toBe('escales.read');
    expect(search.request.params.get('page')).toBe('0');
    search.flush({ content: [], page: { totalElements: 0 } });
  });
});
