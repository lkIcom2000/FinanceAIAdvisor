import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Notification } from '../../api/models/notification';
import { ChatPromptService } from '../../shared/chat-prompt.service';
import { formatDate } from '../../shared/dates';
import { FigurePipe } from '../../shared/figure.pipe';
import { NotificationsService } from '../../shared/notifications.service';
import { ReadOnViewDirective } from '../../shared/read-on-view.directive';
import { WhyLinkComponent } from '../../shared/why-link.component';
import { ICON_PATHS } from './chat-column.component';

/** The status chip of a Notification: plain word and inline Material icon path (UX-DR19). */
export const STATUS_CHIPS: Record<Notification['status'], { label: string; icon: string }> = {
  FOR_YOUR_INFORMATION: {
    label: 'For your information',
    icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
  },
  RECOMMENDATION: { label: 'Recommendation', icon: ICON_PATHS['recommend'] },
  NEEDS_YOUR_OK: {
    label: 'Needs your OK',
    icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z',
  },
  DONE_FOR_YOU: {
    label: 'Done for you',
    icon: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  },
  UNDONE: {
    label: 'Undone',
    icon: 'M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z',
  },
  WITH_AN_ADVISOR: { label: 'With an advisor', icon: ICON_PATHS['support_agent'] },
};

/**
 * The Home tab's Notifications (FR-7, FR-32), newest first from the shared poll; the empty state while there are
 * none. Unread items are bold until expanded or 5 s at least half in view. Every figure and status comes from the
 * backend; this component only formats.
 */
@Component({
  selector: 'app-notification-list',
  imports: [FigurePipe, MatButtonModule, ReadOnViewDirective, WhyLinkComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notification-list.component.html',
  styleUrl: './notification-list.component.scss',
})
export class NotificationListComponent {
  protected readonly feed = inject(NotificationsService);
  private readonly prompt = inject(ChatPromptService);

  protected readonly chips = STATUS_CHIPS;
  protected readonly formatDate = formatDate;
  protected readonly expanded = signal<ReadonlySet<string>>(new Set());

  protected toggle(n: Notification): void {
    const open = new Set(this.expanded());
    if (open.has(n.id)) {
      open.delete(n.id);
    } else {
      open.add(n.id);
      this.feed.markRead(n.id);
    }
    this.expanded.set(open);
  }

  protected ask(n: Notification): void {
    if (n.question) {
      this.prompt.ask(n.question);
    }
  }
}
