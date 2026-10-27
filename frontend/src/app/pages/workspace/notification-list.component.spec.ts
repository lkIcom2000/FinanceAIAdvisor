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
          ({
            target,
            isIntersecting: share > 0,
            intersectionRatio: share,
          }) as IntersectionObserverEntry,
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
    figures: [
      { provId: 'b000000000000001', label: 'Emergency fund', value: '5700.00', unit: 'EUR' },
    ],
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
    actionId: 'a1',
    reason: "This needs your OK because you haven't set up a rule for this.",
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

  describe('Review and confirm', () => {
    const REVIEW = {
      sentence: 'Move 200.00 EUR from Overnight deposit to Current account',
      amountText: '200.00 EUR',
      from: { name: 'Overnight deposit', before: '2700.00', after: '2500.00' },
      to: { name: 'Current account', before: '-90.00', after: '110.00' },
      reason: "This needs your OK because you haven't set up a rule for this.",
      why: 'Fine moves money on its own only within a rule you have set up.',
    };

    function overlay(): HTMLElement {
      return document.querySelector('.cdk-overlay-container') as HTMLElement;
    }

    function openDialog(): void {
      render(UJ1);
      (cards()[2].querySelector('.review') as HTMLButtonElement).click();
      http.expectOne('/api/actions/a1/review').flush(REVIEW);
      fixture.detectChanges();
      vi.advanceTimersByTime(0);
      fixture.detectChanges();
    }

    function button(selector: string): HTMLButtonElement {
      return overlay().querySelector(selector) as HTMLButtonElement;
    }

    it('shows the reason in the expanded item and the button only on items that need an OK', () => {
      render(UJ1);
      expect(cards().map((c) => !!c.querySelector('.review'))).toEqual([false, false, true]);
      (cards()[2].querySelector('.summary') as HTMLButtonElement).click();
      fixture.detectChanges();
      reads();
      expect(text(cards()[2].querySelector('.reason'))).toBe(
        "This needs your OK because you haven't set up a rule for this.",
      );
    });

    it('shows the exact transfer, both accounts before and after, the reason and Why?', () => {
      openDialog();
      reads();
      expect(text(overlay().querySelector('.sentence'))).toBe(REVIEW.sentence);
      expect(Array.from(overlay().querySelectorAll('.account')).map((a) => text(a))).toEqual([
        'Overnight deposit 2,700.00 EUR → after the transfer 2,500.00 EUR',
        'Current account −90.00 EUR → after the transfer 110.00 EUR',
      ]);
      expect(text(overlay().querySelector('.reason'))).toBe(REVIEW.reason);
      expect(overlay().querySelector('.why-text')).toBeNull();
      button('.why').click();
      fixture.detectChanges();
      expect(text(overlay().querySelector('.why-text'))).toBe(REVIEW.why);
      expect(text(button('.confirm'))).toBe('Confirm transfer of 200.00 EUR');
      expect(text(button('.not-now'))).toBe('Not now');
      button('.not-now').click();
      vi.advanceTimersByTime(500);
      http.expectOne('/api/notifications').flush(served);
    });

    it('closes on success, says Done in a snackbar and fetches the list at once', () => {
      openDialog();
      reads();
      button('.confirm').click();
      http.expectOne('/api/actions/a1/confirm').flush({ done: true, amountText: '200.00 EUR' });
      fixture.detectChanges();
      vi.advanceTimersByTime(500);
      fixture.detectChanges();
      const done = UJ1.map((n) => (n.id === 'n1' ? { ...n, status: 'DONE_FOR_YOU' as const } : n));
      http.expectOne('/api/notifications').flush(done);
      fixture.detectChanges();
      expect(overlay().querySelector('app-confirm-action-dialog')).toBeNull();
      expect(text(overlay().querySelector('.mat-mdc-snack-bar-label'))).toBe(
        'Done. 200.00 EUR moved.',
      );
      expect(text(cards()[2].querySelector('.status'))).toBe('Done for you');
      expect(cards()[2].querySelector('.review')).toBeNull();
    });

    it('shows the reason and only Close when the re-check fails', () => {
      openDialog();
      reads();
      button('.confirm').click();
      http.expectOne('/api/actions/a1/confirm').flush({
        done: false,
        amountText: '200.00 EUR',
        reason:
          "I can't make this transfer because your overnight deposit holds only 50.00 EUR now.",
        why: 'Fine checks the balance again right before every transfer.',
      });
      fixture.detectChanges();
      expect(text(overlay().querySelector('.reason'))).toBe(
        "I can't make this transfer because your overnight deposit holds only 50.00 EUR now.",
      );
      expect(overlay().querySelector('.confirm')).toBeNull();
      expect(overlay().querySelector('.not-now')).toBeNull();
      expect(text(button('.close'))).toBe('Close');
      button('.close').click();
      vi.advanceTimersByTime(500);
      http.expectOne('/api/notifications').flush(served);
      expect(overlay().querySelector('.mat-mdc-snack-bar-label')).toBeNull();
    });

    it('says the transfer is no longer waiting when the review answers 409', () => {
      render(UJ1);
      (cards()[2].querySelector('.review') as HTMLButtonElement).click();
      http
        .expectOne('/api/actions/a1/review')
        .flush({ detail: 'x' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      reads();
      expect(text(overlay().querySelector('.reason'))).toBe(
        'This transfer is no longer waiting for your OK.',
      );
      expect(overlay().querySelector('.confirm')).toBeNull();
      button('.close').click();
      vi.advanceTimersByTime(500);
      http.expectOne('/api/notifications').flush(served);
    });

    it('cannot be dismissed while confirming and says so when the answer is lost', () => {
      openDialog();
      reads();
      button('.confirm').click();
      fixture.detectChanges();
      expect(button('.confirm').disabled).toBe(true);
      expect(button('.not-now').disabled).toBe(true);
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();
      expect(overlay().querySelector('app-confirm-action-dialog')).not.toBeNull();
      http.expectOne('/api/actions/a1/confirm').flush(null, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(text(overlay().querySelector('.reason'))).toBe(
        "We couldn't tell whether this transfer was made. Please check your notifications before trying again.",
      );
      button('.close').click();
      vi.advanceTimersByTime(500);
      http.expectOne('/api/notifications').flush(served);
      expect(overlay().querySelector('.mat-mdc-snack-bar-label')).toBeNull();
    });
  });

  describe('Undo transfer', () => {
    const DONE = notification('n4', {
      status: 'DONE_FOR_YOU',
      title: 'I moved 90.00 EUR to your current account',
      actionId: 'a4',
      undoUntil: 'Fri 16 Oct, 5 pm',
      undoable: true,
    });

    function overlay(): HTMLElement {
      return document.querySelector('.cdk-overlay-container') as HTMLElement;
    }

    function undoButton(): HTMLButtonElement | null {
      return cards()[0].querySelector<HTMLButtonElement>('.undo');
    }

    /** Lets the snackbar open and answers the immediate list fetch. */
    function settle(items: Notification[]): void {
      fixture.detectChanges();
      vi.advanceTimersByTime(500);
      http.expectOne('/api/notifications').flush(items);
      fixture.detectChanges();
    }

    it('shows the button with its deadline only while the transfer can be undone', () => {
      render([DONE]);
      expect(text(undoButton())).toBe('Undo transfer until Fri 16 Oct, 5 pm');
      expect(cards()[0].querySelector('.undo-ended')).toBeNull();
    });

    it('says until when undo was possible once the window has ended', () => {
      render([{ ...DONE, undoable: false }]);
      expect(undoButton()).toBeNull();
      expect(text(cards()[0].querySelector('.undo-ended'))).toBe(
        'Undo was possible until Fri 16 Oct, 5 pm',
      );
    });

    it('undoes with one tap, without a dialog, and the item flips to Undone', () => {
      render([DONE]);
      undoButton()!.click();
      reads();
      fixture.detectChanges();
      expect(undoButton()!.disabled).toBe(true);
      expect(document.querySelector('mat-dialog-container')).toBeNull();
      http
        .expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/actions/a4/undo'))
        .flush({ message: 'Transfer undone. 90.00 EUR is back in your overnight deposit.' });
      settle([{ ...DONE, status: 'UNDONE', undoable: false }]);
      expect(text(overlay().querySelector('.mat-mdc-snack-bar-label'))).toBe(
        'Transfer undone. 90.00 EUR is back in your overnight deposit.',
      );
      expect(text(cards()[0].querySelector('.status'))).toBe('Undone');
      expect(undoButton()).toBeNull();
      expect(cards()[0].querySelector('.undo-ended')).toBeNull();
    });

    it('shows the reason of a rejected undo and fetches the list again', () => {
      render([DONE]);
      undoButton()!.click();
      reads();
      http
        .expectOne('/api/actions/a4/undo')
        .flush(
          { detail: 'Undo was possible until Fri 16 Oct, 5 pm.' },
          { status: 409, statusText: 'Conflict' },
        );
      settle([{ ...DONE, undoable: false }]);
      expect(text(overlay().querySelector('.mat-mdc-snack-bar-label'))).toBe(
        'Undo was possible until Fri 16 Oct, 5 pm.',
      );
      expect(undoButton()).toBeNull();
    });
  });
});
