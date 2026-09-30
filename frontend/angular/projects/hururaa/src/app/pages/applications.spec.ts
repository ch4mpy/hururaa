import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { provideApi as provideHururaaApi } from '@api/hururaa-api';
import { MessageService } from 'primeng/api';
import { provideFakeEventStreams } from '../core/resource-events.testing';
import { Applications } from './applications';

// see PfPageStub: pf-ui can't load in jsdom, the stub mirrors pf-page's content queries
vi.mock('pf-ui', async () => ({
  PfPageComponent: (await import('../testing/pf-page.stub')).PfPageStub,
}));

describe('Applications', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Applications],
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

  it('lists only the applications the user has management rights on', async () => {
    const fixture = TestBed.createComponent(Applications);
    http.expectOne('/gateway/me').flush({ directions: [] });
    // the resources fire their requests once the view is rendered: answer them before waiting
    // for the page to be stable
    fixture.detectChanges();
    TestBed.tick();
    http.expectOne('/api/directions').flush([]);

    const request = http.expectOne((r) => r.url === '/api/applications');
    expect(request.request.params.get('manageable')).toBe('true');
    request.flush([
      {
        id: 3,
        clientPrefix: 'escales',
        name: 'Escales',
        direction: 'dpam',
        bffClientId: 'escales-bff',
        apiClientId: 'escales-api',
      },
    ]);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Escales');
  });
});
