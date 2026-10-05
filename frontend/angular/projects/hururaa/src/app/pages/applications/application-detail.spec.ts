import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import {
  Router,
  provideRouter,
  withComponentInputBinding,
  withRouterConfig,
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { provideApi as provideHururaaApi } from '@api/hururaa-api';
import { MessageService } from 'primeng/api';
import { provideFakeEventStreams } from '../../core/resource-events.testing';
import { ApplicationDetail } from './application-detail';

@Component({ selector: 'app-tab-stub', template: '' })
class TabStub {}

describe('ApplicationDetail', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    // the scrollable tabs observe their size, jsdom has no ResizeObserver
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            {
              path: 'applications/:direction/:applicationId',
              component: ApplicationDetail,
              children: [
                { path: '', pathMatch: 'full', redirectTo: 'groups' },
                { path: 'groups', component: TabStub },
                { path: 'roles', component: TabStub },
              ],
            },
          ],
          withComponentInputBinding(),
          withRouterConfig({ paramsInheritanceStrategy: 'always' }),
        ),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideGatewayApi('/gateway'),
        provideHururaaApi('/api'),
        provideFakeEventStreams([]),
        MessageService,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  // regression: the child route is activated after this component is created, its own
  // snapshot is not set yet when the current tab is first read
  it('opens on the tab of the URL, even navigated to from another route', async () => {
    const harness = await RouterTestingHarness.create();
    // the router itself rather than the harness, which would wait for the pending requests
    await TestBed.inject(Router).navigateByUrl('/applications/daf/4/roles');
    harness.detectChanges();
    TestBed.tick();
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['daf'] });
    TestBed.tick();
    http.expectOne('/api/me/delegations').flush({
      hururaaRoles: [],
      platformOrganization: 'dsi',
      administeredDirections: ['daf'],
      managedApplications: [],
    });
    http.expectOne('/api/applications/4').flush({
      id: 4,
      clientPrefix: 'anahei',
      name: 'Anahei',
      direction: 'daf',
      bffClientId: 'anahei-bff',
      apiClientId: 'anahei-api',
    });
    harness.detectChanges();
    TestBed.tick();
    // the application's roles, read once it is known to be manageable
    http.expectOne('/api/directions/daf/applications/4/roles').flush([]);
    await harness.fixture.whenStable();

    const page = harness.routeNativeElement!;
    expect(page.querySelector('h2')?.textContent).toContain('Anahei');
    expect(page.querySelector('[role="tab"][aria-selected="true"]')?.textContent).toContain(
      'Rôles',
    );
    expect(page.querySelector('app-tab-stub')).not.toBeNull();
  });
});
