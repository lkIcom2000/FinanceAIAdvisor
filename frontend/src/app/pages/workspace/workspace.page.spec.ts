import { HttpClient } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SessionService } from '../../core/session.service';
import {
  answerSessionLoad,
  flushMicrotasks,
  httpTestingProviders,
} from '../../testing/session-testing';
import { FigureLinksService } from '../../shared/figure-links.service';
import { WorkspacePage } from './workspace.page';

describe('WorkspacePage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkspacePage],
      providers: [provideRouter([]), ...httpTestingProviders()],
    }).compileComponents();
    const loaded = TestBed.inject(SessionService).load();
    await answerSessionLoad({ role: 'PERSONA', displayName: 'Lena' });
    await loaded;
  });

  function answerPersona(today?: string): void {
    TestBed.inject(HttpTestingController)
      .expectOne('/api/persona')
      .flush({ id: 'p-lena', displayName: 'Lena', ...(today ? { today } : {}) });
  }

  it('shows the header and the four Overview tabs', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.wordmark')?.textContent).toBe('Atruvia');
    expect(element.querySelector('.product')?.textContent).toBe('Fine');
    expect(element.querySelector('.user')?.textContent).toBe('Lena');
    expect(element.textContent).toContain('Log out');
    const labels = Array.from(element.querySelectorAll('[role="tab"]')).map((t) =>
      t.textContent?.trim(),
    );
    expect(labels).toEqual(['Home', 'Liquidity', 'Wealth & Retirement', 'Letters']);
    expect(element.querySelector('aside[aria-label="Chat with Fine"]')).not.toBeNull();
    expect(element.querySelector('.date-chip')).toBeNull();
  });

  it('mounts the Home tab content under the Home tab only', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    TestBed.inject(HttpTestingController).expectOne('/api/overview/home');
    const homeTabs = element.querySelectorAll('app-home-tab');
    expect(homeTabs.length).toBe(1);
    expect(homeTabs[0].closest('[data-tab]')?.getAttribute('data-tab')).toBe('home');
    for (const key of ['liquidity', 'wealth', 'letters']) {
      expect(element.querySelector(`[data-tab="${key}"] app-home-tab`)).toBeNull();
    }
  });

  it('shows the simulated-date chip when the backend sends today (research profile)', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona('2026-10-20');
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.date-chip')?.textContent).toBe(
      'Today: 20 Oct 2026',
    );
  });

  it('selects the tab named in ?tab= and falls back to Home', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.componentRef.setInput('tab', 'wealth');
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();
    const selected = () =>
      (fixture.nativeElement as HTMLElement)
        .querySelector('[role="tab"][aria-selected="true"]')
        ?.textContent?.trim();
    expect(selected()).toBe('Wealth & Retirement');

    fixture.componentRef.setInput('tab', 'nonsense');
    await fixture.whenStable();
    expect(selected()).toBe('Home');
  });

  it('writes the chosen tab into the URL', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();

    (
      (fixture.nativeElement as HTMLElement).querySelectorAll('[role="tab"]')[1] as HTMLElement
    ).click();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { tab: 'liquidity' } }),
    );
  });

  it('keeps the scroll position of each tab for the session', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const overview = element.querySelector('.overview') as HTMLElement;
    let top = 0;
    Object.defineProperty(overview, 'scrollTop', {
      configurable: true,
      get: () => top,
      set: (value: number) => (top = value),
    });
    const tabs = () => element.querySelectorAll('[role="tab"]') as NodeListOf<HTMLElement>;

    top = 300;
    // The tab body reports the end of its slide (in jsdom after the 100 ms fallback); then the position returns.
    const switchTo = async (index: number) => {
      tabs()[index].click();
      fixture.detectChanges();
      await new Promise((resolve) => setTimeout(resolve, 150));
      await fixture.whenStable();
    };

    await switchTo(1);
    expect(top).toBe(0);

    top = 40;
    await switchTo(0);
    expect(top).toBe(300);

    await switchTo(1);
    expect(top).toBe(40);
  });

  it('keeps the scroll position of each tab when ?tab= changes the tab', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.componentRef.setInput('tab', 'home');
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();
    const overview = (fixture.nativeElement as HTMLElement).querySelector('.overview') as HTMLElement;
    let top = 0;
    Object.defineProperty(overview, 'scrollTop', {
      configurable: true,
      get: () => top,
      set: (value: number) => (top = value),
    });
    const switchTo = async (tab: string) => {
      fixture.componentRef.setInput('tab', tab);
      fixture.detectChanges();
      await new Promise((resolve) => setTimeout(resolve, 150));
      await fixture.whenStable();
    };

    top = 300;
    await switchTo('wealth');
    expect(top).toBe(0);

    top = 70;
    await switchTo('home');
    expect(top).toBe(300);

    await switchTo('wealth');
    expect(top).toBe(70);
  });

  it('keeps the chat at 440 px, 500 px from 1440 px, next to an Overview of at least 560 px', () => {
    TestBed.createComponent(WorkspacePage).detectChanges();
    const rules = Array.from(document.styleSheets).flatMap((sheet) => Array.from(sheet.cssRules));
    const workspace = rules.find(
      (r): r is CSSStyleRule => r instanceof CSSStyleRule && /\.workspace/.test(r.selectorText),
    );
    expect(workspace?.style.getPropertyValue('grid-template-columns')).toBe(
      'minmax(560px, 1fr) var(--fine-chat-width)',
    );
    const chat = (rule: CSSRule | undefined) =>
      Number(/--fine-chat-width:\s*(\d+)px/.exec(rule?.cssText ?? '')?.[1]);
    const narrow = chat(workspace);
    const media = rules.find(
      (r): r is CSSMediaRule => r instanceof CSSMediaRule && /min-width:\s*1440px/.test(r.conditionText),
    );
    const wide = chat(media?.cssRules[0]);
    expect(narrow).toBe(440);
    expect(wide).toBe(500);
    // The Overview's 560 px minimum fits next to the chat at both reference widths.
    expect(1366 - narrow).toBeGreaterThanOrEqual(560);
    expect(1440 - wide).toBeGreaterThanOrEqual(560);
  });

  it('logs out on the server and returns to Login', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();

    ((fixture.nativeElement as HTMLElement).querySelector('.logout') as HTMLElement).click();
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.method === 'POST' && r.url === '/api/auth/logout')
      .flush(null, { status: 204, statusText: 'No Content' });
    await flushMicrotasks();

    expect(TestBed.inject(SessionService).user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('still returns to Login when the logout call fails', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();

    ((fixture.nativeElement as HTMLElement).querySelector('.logout') as HTMLElement).click();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/logout')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    await flushMicrotasks();

    expect(TestBed.inject(SessionService).user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('returns to Login with the expiry message when the session has expired', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const request = firstValueFrom(TestBed.inject(HttpClient).get('/api/persona')).catch(
      () => undefined,
    );
    TestBed.inject(HttpTestingController)
      .expectOne('/api/persona')
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    await request;
    await flushMicrotasks();

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
    expect(TestBed.inject(SessionService).user()).toBeNull();
  });

  it('dots the label of a tab with a referenced figure and switches only on "Show in overview"', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.detectChanges();
    answerPersona();
    await fixture.whenStable();
    const links = TestBed.inject(FigureLinksService);
    links.index.set(new Map([['wea0000000000a3', 'wealth']]));

    links.highlight(['wea0000000000a3']);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const tabs = element.querySelectorAll('[role="tab"]');
    expect(tabs[2].querySelector('.tab-dot')).not.toBeNull();
    expect(tabs[0].querySelector('.tab-dot')).toBeNull();
    expect(navigate).not.toHaveBeenCalled();

    links.show('wea0000000000a3');
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { tab: 'wealth' } }),
    );
  });
});
