import { HttpClient } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SessionService } from '../../core/session.service';
import { answerSessionLoad, flushMicrotasks, httpTestingProviders } from '../../testing/session-testing';
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

  it('shows the header and the four Overview tabs', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.wordmark')?.textContent).toBe('Atruvia');
    expect(element.querySelector('.product')?.textContent).toBe('Fine');
    expect(element.querySelector('.user')?.textContent).toBe('Lena');
    expect(element.textContent).toContain('Log out');
    const labels = Array.from(element.querySelectorAll('[role="tab"]')).map((t) => t.textContent?.trim());
    expect(labels).toEqual(['Home', 'Liquidity', 'Wealth & Retirement', 'Letters']);
    expect(element.querySelector('aside[aria-label="Chat with Fine"]')).not.toBeNull();
  });

  it('selects the tab named in ?tab= and falls back to Home', async () => {
    const fixture = TestBed.createComponent(WorkspacePage);
    fixture.componentRef.setInput('tab', 'wealth');
    await fixture.whenStable();
    const selected = () =>
      (fixture.nativeElement as HTMLElement).querySelector('[role="tab"][aria-selected="true"]')?.textContent?.trim();
    expect(selected()).toBe('Wealth & Retirement');

    fixture.componentRef.setInput('tab', 'nonsense');
    await fixture.whenStable();
    expect(selected()).toBe('Home');
  });

  it('writes the chosen tab into the URL', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(WorkspacePage);
    await fixture.whenStable();

    ((fixture.nativeElement as HTMLElement).querySelectorAll('[role="tab"]')[1] as HTMLElement).click();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { tab: 'liquidity' } }));
  });

  it('returns to Login with the expiry message when the session has expired', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const request = firstValueFrom(TestBed.inject(HttpClient).get('/api/persona')).catch(() => undefined);
    TestBed.inject(HttpTestingController)
      .expectOne('/api/persona')
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    await request;
    await flushMicrotasks();

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
    expect(TestBed.inject(SessionService).user()).toBeNull();
  });
});
