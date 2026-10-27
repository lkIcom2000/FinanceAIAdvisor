import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { httpTestingProviders } from '../testing/session-testing';
import { HIDDEN_POLL_MS, NotificationsService, VISIBLE_POLL_MS } from './notifications.service';
import { notification } from '../testing/notification-testing';

describe('NotificationsService', () => {
  let http: HttpTestingController;
  let service: NotificationsService;
  let visibility: DocumentVisibilityState;

  beforeEach(() => {
    vi.useFakeTimers();
    visibility = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    TestBed.configureTestingModule({ providers: [...httpTestingProviders()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(NotificationsService);
  });

  afterEach(() => {
    service.stop();
    http.verify();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  function setVisibility(state: DocumentVisibilityState): void {
    visibility = state;
    document.dispatchEvent(new Event('visibilitychange'));
  }

  it('polls at once, then every 10 s while visible and every 60 s while hidden', () => {
    service.start();
    http.expectOne('/api/notifications').flush([notification('n1')]);
    expect(service.items().map((n) => n.id)).toEqual(['n1']);
    expect(service.loaded()).toBe(true);

    vi.advanceTimersByTime(VISIBLE_POLL_MS - 1);
    http.expectNone('/api/notifications');
    vi.advanceTimersByTime(1);
    http.expectOne('/api/notifications').flush([notification('n2'), notification('n1')]);
    expect(service.items().map((n) => n.id)).toEqual(['n2', 'n1']);

    setVisibility('hidden');
    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    http.expectNone('/api/notifications');
    vi.advanceTimersByTime(HIDDEN_POLL_MS - VISIBLE_POLL_MS);
    http.expectOne('/api/notifications').flush([]);

    setVisibility('visible');
    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    http.expectOne('/api/notifications').flush([]);
  });

  it('keeps one request in flight and the last list when a poll fails', () => {
    service.start();
    http.expectOne('/api/notifications').flush([notification('n1')]);
    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    const pending = http.expectOne('/api/notifications');

    setVisibility('visible');
    vi.advanceTimersByTime(VISIBLE_POLL_MS * 3);
    http.expectNone('/api/notifications');

    pending.flush('down', { status: 503, statusText: 'Service Unavailable' });
    expect(service.items().map((n) => n.id)).toEqual(['n1']);
    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    http.expectOne('/api/notifications').flush([notification('n1')]);
  });

  it('counts unread items and marks one read with a single POST that a later poll does not undo', () => {
    service.start();
    http
      .expectOne('/api/notifications')
      .flush([notification('n1'), notification('n2'), notification('n3', { read: true })]);
    expect(service.unread()).toBe(2);

    service.markRead('n1');
    service.markRead('n1');
    const post = http.expectOne({ method: 'POST', url: '/api/notifications/n1/read' });
    expect(service.unread()).toBe(1);

    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    http.expectOne('/api/notifications').flush([notification('n1'), notification('n2')]);
    expect(service.unread()).toBe(1);
    post.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('forgets the list on stop and ignores an answer still in flight', () => {
    service.start();
    http.expectOne('/api/notifications').flush([notification('n1')]);
    vi.advanceTimersByTime(VISIBLE_POLL_MS);
    const late = http.expectOne('/api/notifications');

    service.stop();
    expect(late.cancelled).toBe(true);
    expect(service.items()).toEqual([]);
    expect(service.unread()).toBe(0);
    expect(service.loaded()).toBe(false);
    vi.advanceTimersByTime(HIDDEN_POLL_MS);
    http.expectNone('/api/notifications');
  });

  it('shows an item unread again when marking it read fails', () => {
    service.start();
    http.expectOne('/api/notifications').flush([notification('n1')]);

    service.markRead('n1');
    http
      .expectOne('/api/notifications/n1/read')
      .flush('down', { status: 503, statusText: 'Service Unavailable' });

    expect(service.unread()).toBe(1);
  });
});
