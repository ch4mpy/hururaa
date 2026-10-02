import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { provideApi as provideHururaaApi } from '@api/hururaa-api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { PermissionHistory } from './permission-history';

describe('PermissionHistory', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PermissionHistory],
      providers: [
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

  function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(PermissionHistory);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    http.expectOne('/gateway/me').flush({ directions: [] });
    fixture.detectChanges();
    TestBed.tick();
    return fixture;
  }

  it("describes a direction's changes with their author and the user concerned", async () => {
    const fixture = render({ direction: 'dpam' });

    const request = http.expectOne((r) => r.url === '/api/directions/dpam/history');
    expect(request.request.params.getAll('categories')).toBeNull();
    request.flush({
      content: [
        {
          timestamp: '2026-10-01T08:00:00Z',
          type: 'GROUP_ROLE_GRANTED',
          category: 'GROUP_ROLE',
          authorId: 'a',
          authorUsername: 'dpam.admin',
          authorFirstName: 'Teva',
          authorLastName: 'Tetuanui',
          group: 'escales.agent',
          role: 'escales.stopovers.read',
          applicationName: 'Escales',
        },
      ],
      page: { size: 10, number: 0, totalElements: 1, totalPages: 1 },
    });
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain(
      'Le groupe escales.agent attribue le rôle escales.stopovers.read de Escales',
    );
    expect(text).toContain('Teva Tetuanui');
  });

  it("asks an application's history, filtered by the chosen categories", async () => {
    const fixture = render({ direction: 'dpam', applicationId: 3 });
    http
      .expectOne((r) => r.url === '/api/directions/dpam/applications/3/history')
      .flush({
        content: [],
      });
    await fixture.whenStable();

    (fixture.componentInstance as unknown as { filter(c: string[]): void }).filter([
      'APPLICATION_ROLE',
      'GROUP_ROLE',
    ]);
    TestBed.tick();

    const request = http.expectOne((r) => r.url === '/api/directions/dpam/applications/3/history');
    expect(request.request.params.getAll('categories')).toEqual(['APPLICATION_ROLE', 'GROUP_ROLE']);
    request.flush({ content: [] });
  });

  it("asks a group's history", () => {
    render({ direction: 'dpam', group: 'escales.agent' });

    http
      .expectOne((r) => r.url === '/api/directions/dpam/groups/escales.agent/history')
      .flush({ content: [] });
  });
});
