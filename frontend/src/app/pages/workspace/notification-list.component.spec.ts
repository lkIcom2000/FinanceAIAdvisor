import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Notification } from '../../api/models/notification';
import { ChatPromptService } from '../../shared/chat-prompt.service';
import { NotificationsService, VISIBLE_POLL_MS } from '../../shared/notifications.service';
import { READ_AFTER_MS } from '../../shared/read-on-view.directive';
import { notification } from '../../testing/notification-testing';
import { httpTestingProviders } from '../../testing/session-testing';
import { NotificationListComponent } from './notification-list.component';

/** A controllable IntersectionObserver: `show(share)` reports that share of every observed element in view. */
class FakeIntersectionObserver {
  static all: FakeIntersectionObserver[] = [];
  private readonly targets: Element[] = [];

  constructor(private readonly callback: IntersectionObserverCallback) {
    FakeIntersectionObserver.all.push(this);
  }

  observe(target: Element): void {
    this.targets.push(target);
  }

  disconnect(): void {
    this.targets.length = 0;
  }

  static show(share: number): void {
    for (const o of FakeIntersectionObserver.all) {
      const entries = o.targets.map(
        (target) =>
          ({ target, isIntersecting: share > 0, intersectionRatio: share }) as IntersectionObserverEntry,
      );
      if (entries.length) {
        o.callback(entries, o as unknown as IntersectionObserver);
      }
    }
  }
}

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

const UJ1: Notification[] = [
  notification('n3', {
    status: 'FOR_YOUR_INFORMATION',
    title: 'Your emergency fund is below its target',
    figures: [{ provId: 'b000000000000001', label: 'Emergency fund', value: '5700.00', unit: 'EUR' }],
  }),
  notification('n2', {
    status: 'RECOMMENDATION',
    title: 'Paying monthly could avoid this shortfall',
    body: 'Your yearly premium is due.',
    question: 'Should I pay my premium monthly instead of yearly?',
    figures: [{ provId: 'b000000000000002', label: 'Premium', value: '480.00', unit: 'EUR' }],
  }),
  notification('n1', {
    status: 'NEEDS_YOUR_OK',
    title: 'Your current account may run short on 1 Nov 2026',
    figures: [{ provId: 'b000000000000003', label: 'Shortfall', value: '-90.00', unit: 'EUR' }],
  }),
];

describe('NotificationListComponent', () => {
  let fixture: ComponentFixture<NotificationListComponent>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let feed: NotificationsService;
  let served: Notification[];

  beforeEach(async () => {
    vi.useFakeTimers();
    FakeIntersectionObserver.all = [];
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    await TestBed.configureTestingModule({
      imports: [NotificationListComponent],
      providers: [...httpTestingProviders()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    feed = TestBed.inject(NotificationsService);
  });

  afterEach(() => {
    feed.stop();
    http.verify();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  function render(items: Notification[]): void {
    fixture = TestBed.createComponent(NotificationListComponent);
    served = items;
    feed.start();
    http.expectOne('/api/notifications').flush(items);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function cards(): HTMLElement[] {
    return Array.from(element.querySelectorAll<HTMLElement>('.notification'));
  }

  /** Advances the clock; a poll that falls due meanwhile gets the same list again. */
  function advance(ms: number): void {
    for (let left = ms; left > 0; left -= 1000) {
      vi.advanceTimersByTime(Math.min(1000, left));
      http.match('/api/notifications').forEach((r) => r.flush(served));
    }
  }

  function reads(): number {
    return http.match((r) => r.url.endsWith('/read')).map((r) => r.flush(null)).length;
  }

  it('keeps the empty state without notifications', () => {
    render([]);
    expect(text(element.querySelector('.empty'))).toContain(
      'Nothing needs your attention right now.',
    );
    expect(cards()).toEqual([]);
  });

  it('shows status chip, title, date and figure chips with Why? in the backend order', () => {
    render(UJ1);
    expect(cards().map((c) => text(c.querySelector('.status')))).toEqual([
      'For your information',
      'Recommendation',
      'Needs your OK',
    ]);
    expect(cards().every((c) => c.querySelector('.status svg'))).toBe(true);
    expect(text(cards()[2].querySelector('.title'))).toBe(
      'Your current account may run short on 1 Nov 2026',
    );
    expect(text(cards()[2].querySelector('.date'))).toBe('15 Oct 2026');
    expect(text(cards()[2].querySelector('.figure-chip'))).toBe('Shortfall −90.00 EUR');
    expect(cards().every((c) => c.querySelector('.figure app-why-link button'))).toBe(true);
    expect(cards().every((c) => c.classList.contains('unread'))).toBe(true);
    expect(element.querySelector('.body')).toBeNull();
  });

  it('expanding shows the explanation and marks the item read with one POST', () => {
    render(UJ1);
    const summary = cards()[1].querySelector<HTMLButtonElement>('.summary')!;
    summary.click();
    fixture.detectChanges();

    expect(summary.getAttribute('aria-expanded')).toBe('true');
    expect(text(cards()[1].querySelector('.body'))).toBe('Your yearly premium is due.');
    expect(cards()[1].classList).not.toContain('unread');
    http.expectOne({ method: 'POST', url: '/api/notifications/n2/read' }).flush(null);

    summary.click();
    summary.click();
    fixture.detectChanges();
    expect(reads()).toBe(0);
  });

  it('marks unread items read after 5 s at least half in view, not sooner and not when scrolled away', () => {
    render(UJ1.slice(0, 2));

    FakeIntersectionObserver.show(0.3);
    advance(READ_AFTER_MS);
    expect(reads()).toBe(0);

    FakeIntersectionObserver.show(0.6);
    advance(READ_AFTER_MS - 1);
    FakeIntersectionObserver.show(0);
    advance(READ_AFTER_MS);
    expect(reads()).toBe(0);

    FakeIntersectionObserver.show(0.6);
    advance(READ_AFTER_MS);
    expect(reads()).toBe(2);
    fixture.detectChanges();
    expect(cards().some((c) => c.classList.contains('unread'))).toBe(false);
  });

  it('sends the prepared question of a Recommendation to the chat', () => {
    render(UJ1);
    for (const c of cards()) {
      c.querySelector<HTMLButtonElement>('.summary')!.click();
    }
    fixture.detectChanges();
    expect(reads()).toBe(3);

    const asks = element.querySelectorAll<HTMLButtonElement>('.ask');
    expect(asks.length).toBe(1);
    expect(text(asks[0])).toBe('Ask Fine about this');
    asks[0].click();
    expect(TestBed.inject(ChatPromptService).pending()).toBe(
      'Should I pay my premium monthly instead of yearly?',
    );
  });

  // jsdom has no layout: this checks that existing items keep their DOM nodes; scroll anchoring is CSS.
  it('inserts a new item and keeps the DOM nodes of the existing ones', () => {
    render(UJ1.slice(1));
    const before = cards();
    const scroller = document.createElement('div');
    document.body.appendChild(scroller);
    scroller.appendChild(element);
    scroller.scrollTop = 120;

    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    http.expectOne('/api/notifications').flush(UJ1);
    fixture.detectChanges();

    expect(cards().length).toBe(3);
    expect(cards().slice(1)).toEqual(before);
    expect(cards()[1]).toBe(before[0]);
    expect(scroller.scrollTop).toBe(120);
    scroller.remove();
  });
});
