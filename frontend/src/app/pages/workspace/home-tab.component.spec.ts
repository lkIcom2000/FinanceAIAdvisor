import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { httpTestingProviders } from '../../testing/session-testing';
import { HomeTabComponent } from './home-tab.component';

describe('HomeTabComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeTabComponent],
      providers: [...httpTestingProviders()],
    }).compileComponents();
  });

  it('shows skeletons, then the current account card with the backend figure and Why?', async () => {
    const fixture = TestBed.createComponent(HomeTabComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.skeleton').length).toBe(3);

    TestBed.inject(HttpTestingController)
      .expectOne('/api/overview/home')
      .flush({
        currentAccount: {
          provId: 'aaaaaaaaaaaaaaaa',
          label: 'Current account balance',
          value: '1974.00',
          unit: 'EUR',
        },
      });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelectorAll('.skeleton').length).toBe(0);
    expect(element.querySelector('.label')?.textContent).toBe('Current account');
    expect(element.querySelector('.figure-lg')?.textContent?.trim()).toBe('1,974.00 EUR');
    expect(element.querySelector('app-why-link button')?.textContent).toBe('Why?');
    expect(element.textContent).not.toContain('aaaaaaaaaaaaaaaa');
  });

  it('marks a negative balance and keeps the real minus sign', async () => {
    const fixture = TestBed.createComponent(HomeTabComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/overview/home')
      .flush({
        currentAccount: { provId: 'aaaaaaaaaaaaaaaa', label: 'x', value: '-90.00', unit: 'EUR' },
      });
    await fixture.whenStable();
    fixture.detectChanges();

    const figure = (fixture.nativeElement as HTMLElement).querySelector('.figure-lg');
    expect(figure?.textContent?.trim()).toBe('−90.00 EUR');
    expect(figure?.classList).toContain('negative');
  });

  it('says so when the overview cannot be loaded', async () => {
    const fixture = TestBed.createComponent(HomeTabComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/overview/home')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain("We couldn't load your overview.");
  });
});
