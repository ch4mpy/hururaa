import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ErrorBannerService } from '../core/error-banner.service';
import { ErrorBanner } from './error-banner';

describe('ErrorBanner', () => {
  let fixture: ComponentFixture<ErrorBanner>;
  let banner: ErrorBannerService;

  const element = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorBanner],
      providers: [provideRouter([{ path: '**', children: [] }])],
    }).compileComponents();
    banner = TestBed.inject(ErrorBannerService);
    fixture = TestBed.createComponent(ErrorBanner);
    await fixture.whenStable();
  });

  it('renders nothing while there is no message', () => {
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });

  it('announces the latest message', async () => {
    banner.show('Première erreur');
    banner.show('Seconde erreur');
    await fixture.whenStable();

    const alert = element().querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Seconde erreur');
    expect(alert?.textContent).not.toContain('Première erreur');
  });

  it('is dismissed with its close button', async () => {
    banner.show('Une erreur');
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('button.p-message-close-button')?.click();
    await fixture.whenStable();

    expect(element().querySelector('[role="alert"]')).toBeNull();
  });

  it('is cleared when navigating to another page', async () => {
    banner.show('Une erreur');
    await fixture.whenStable();

    await TestBed.inject(Router).navigateByUrl('/elsewhere');
    await fixture.whenStable();

    expect(element().querySelector('[role="alert"]')).toBeNull();
  });
});
