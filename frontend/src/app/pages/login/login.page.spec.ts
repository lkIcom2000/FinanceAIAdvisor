import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BrowserLocation } from '../../core/browser-location';
import { FakeBrowserLocation, flushMicrotasks, httpTestingProviders } from '../../testing/session-testing';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), ...httpTestingProviders()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function submit(fixture: ReturnType<typeof TestBed.createComponent<LoginPage>>, user: string) {
    const element = fixture.nativeElement as HTMLElement;
    const [username, password] = Array.from(element.querySelectorAll('input'));
    username.value = user;
    username.dispatchEvent(new Event('input'));
    password.value = 'secret';
    password.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await flushMicrotasks();
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await flushMicrotasks();
    return http.expectOne('/api/auth/login');
  }

  it('shows no hint that research functions exist', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent?.toLowerCase()).not.toMatch(/research|swagger/);
  });

  it('shows the inline error for wrong credentials', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();

    (await submit(fixture, 'p-lena')).flush(
      { code: 'BAD_CREDENTIALS' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      "That username and password don't match.",
    );
  });

  it('treats a failed login as wrong credentials even while a session is known', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    (await submit(fixture, 'p-lena')).flush({ role: 'PERSONA', displayName: 'Lena' });
    await flushMicrotasks();

    (await submit(fixture, 'p-marco')).flush(
      { code: 'BAD_CREDENTIALS' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();

    expect(navigate).not.toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      "That username and password don't match.",
    );
  });

  it('does not send whitespace-only input', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const [username, password] = Array.from(element.querySelectorAll('input'));
    username.value = '   ';
    username.dispatchEvent(new Event('input'));
    password.value = 'secret';
    password.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await flushMicrotasks();

    http.expectNone('/api/auth/csrf');
    http.expectNone('/api/auth/login');
  });

  it('shows the inactivity message after an expired session', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('reason', 'expired');
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'You were logged out after a while of inactivity. Please log in again.',
    );
  });

  it('sends a persona to the workspace', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();

    (await submit(fixture, 'p-lena')).flush({ role: 'PERSONA', displayName: 'Lena' });
    await flushMicrotasks();

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('sends the advisor to the advisor Swagger UI group', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();

    (await submit(fixture, 'advisor-kaya')).flush({ role: 'ADVISOR', displayName: 'Kaya' });
    await flushMicrotasks();

    const location = TestBed.inject(BrowserLocation) as unknown as FakeBrowserLocation;
    expect(location.assigned).toEqual(['/swagger-ui/index.html?urls.primaryName=advisor']);
  });

  afterEach(() => http.verify());
});
