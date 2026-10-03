import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Home } from './home';

// see PfPageStub: pf-ui can't load in jsdom, the stub mirrors pf-page's content queries
vi.mock('pf-ui', async () => ({
  PfPageComponent: (await import('../testing/pf-page.stub')).PfPageStub,
  PfTagComponent: (await import('../testing/pf-tag.stub')).PfTagStub,
}));

describe('Home', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideNoopAnimations()],
    }).compileComponents();
  });

  it('has its own page title, not the title of one of its cards', async () => {
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();

    // pf-page takes the first `#title` template of its content: the page's own must be it
    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent?.trim()).toBe('Identification et autorisation des utilisateurs');
  });
});
