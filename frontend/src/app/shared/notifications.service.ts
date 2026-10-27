import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, finalize } from 'rxjs';
import { Notification } from '../api/models/notification';
import { NotificationsService as NotificationsApi } from '../api/services/notifications.service';

/** Poll interval while the page is visible (UX-DR40). */
export const VISIBLE_POLL_MS = 10_000;

/** Poll interval while the page is hidden. */
export const HIDDEN_POLL_MS = 60_000;

/**
 * The persona's Notifications for the Home tab and its badge (FR-32): one request in flight at a time, every 10 s while
 * the page is visible and every 60 s while hidden. A failed poll keeps the last list silently. Read state lives on
 * the server; a read marked here shows at once and stays even if a poll answered before the POST.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly api = inject(NotificationsApi);
  private readonly document = inject(DOCUMENT);

  private readonly list = signal<readonly Notification[]>([]);
  private readonly readHere = signal<ReadonlySet<string>>(new Set());

  /** `false` until the first poll answered (or failed). */
  readonly loaded = signal(false);

  /** Newest first, as the backend orders them. */
  readonly items = computed(() => {
    const read = this.readHere();
    return this.list().map((n) => (!n.read && read.has(n.id) ? { ...n, read: true } : n));
  });

  readonly unread = computed(() => this.items().filter((n) => !n.read).length);

  private running = false;
  private inFlight = false;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private request: Subscription | undefined;
  private readonly onVisibility = () => {
    if (!this.inFlight) {
      this.schedule();
    }
  };

  /** Starts polling at once; a second call while running does nothing. */
  start(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    this.document.addEventListener('visibilitychange', this.onVisibility);
    this.poll();
  }

  /** Stops polling and forgets the list, so a later login never shows the previous persona's items. */
  stop(): void {
    this.running = false;
    clearTimeout(this.timer);
    this.request?.unsubscribe();
    this.inFlight = false;
    this.document.removeEventListener('visibilitychange', this.onVisibility);
    this.list.set([]);
    this.readHere.set(new Set());
    this.loaded.set(false);
  }

  /** Polls at once, e.g. after a confirmation changed an item; a request in flight is replaced. */
  refresh(): void {
    if (!this.running) {
      return;
    }
    this.request?.unsubscribe();
    this.poll();
  }

  /** Marks an unread Notification read with one POST; a failed POST shows it unread again. */
  markRead(id: string): void {
    const item = this.items().find((n) => n.id === id);
    if (!item || item.read) {
      return;
    }
    this.readHere.update((s) => new Set(s).add(id));
    this.api.markRead({ id }).subscribe({
      error: () =>
        this.readHere.update((s) => {
          const next = new Set(s);
          next.delete(id);
          return next;
        }),
    });
  }

  private poll(): void {
    if (this.inFlight || !this.running) {
      return;
    }
    clearTimeout(this.timer);
    this.inFlight = true;
    this.request = this.api
      .list()
      .pipe(
        finalize(() => {
          if (this.running) {
            this.inFlight = false;
            this.loaded.set(true);
            this.schedule();
          }
        }),
      )
      .subscribe({ next: (items) => this.list.set(items), error: () => undefined });
  }

  private schedule(): void {
    clearTimeout(this.timer);
    if (!this.running) {
      return;
    }
    const visible = this.document.visibilityState === 'visible';
    this.timer = setTimeout(() => this.poll(), visible ? VISIBLE_POLL_MS : HIDDEN_POLL_MS);
  }
}
