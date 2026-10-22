import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ChatMessage } from '../../api/models/chat-message';
import { ChatService } from '../../api/services/chat.service';
import { ChatComposerComponent } from './chat-composer.component';

export type Outcome = NonNullable<ChatMessage['outcome']>;

/** Outcome label row of a response card: plain words and a Material Symbols icon (DESIGN.md). */
export const OUTCOME_LABELS: Record<Outcome, { label: string; icon: string }> = {
  INFORM: { label: 'Explanation', icon: 'menu_book' },
  COMPUTE: { label: 'Calculated for you', icon: 'calculate' },
  RECOMMEND_CATEGORY: { label: 'Recommendation', icon: 'recommend' },
  ASK_PROFILE: { label: 'A few questions first', icon: 'assignment_ind' },
  REFUSE_OUT_OF_SCOPE: { label: 'Not part of this service', icon: 'do_not_disturb_on' },
  DETECT_TRAP: { label: "Let's check the question", icon: 'report' },
  ESCALATE: { label: 'Passed to a human advisor', icon: 'support_agent' },
};

/** Inline icon paths (no icon font is bundled); outcomes of later stories add theirs. */
export const ICON_PATHS: Record<string, string> = {
  menu_book:
    'M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z',
  chat: 'M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 9h12v2H6V9zm8 5H6v-2h8v2zm4-6H6V6h12v2z',
};

/** Until Epic 8 tailors them to the persona's notifications. */
export const SUGGESTIONS = [
  'How does an emergency fund work?',
  'What is an ETF savings plan?',
  'How is my liquidity looking?',
] as const;

export const WAITING_STEPS = [
  'Understanding your question',
  'Getting your figures',
  'Writing the answer',
  'Checking the rules',
] as const;

/** The waiting card advances one step per interval; it never confirms a step and never times out. */
export const STEP_INTERVAL_MS = 3000;
export const SLOW_AFTER_MS = 20_000;

/** A question whose answer is being made, or whose answer call failed (it stays saved). */
type Pending =
  { status: 'waiting'; messageId: string | null } | { status: 'failed'; messageId: string };

type LoadState = 'loading' | 'ready' | 'failed';

/**
 * The chat column: greeting, message log, waiting and failed cards, response cards and the composer. A turn is two
 * calls: posting saves the question, answering returns one complete, checked answer. Try again repeats only the
 * answer call.
 */
@Component({
  selector: 'app-chat-column',
  imports: [ChatComposerComponent, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-column.component.html',
  styleUrl: './chat-column.component.scss',
})
export class ChatColumnComponent {
  private readonly chat = inject(ChatService);
  private readonly injector = inject(Injector);

  protected readonly suggestions = SUGGESTIONS;
  protected readonly steps = WAITING_STEPS;
  protected readonly outcomes = OUTCOME_LABELS;
  protected readonly icons = ICON_PATHS;

  protected readonly load = signal<LoadState>('loading');
  protected readonly personaName = signal('');
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly pending = signal<Pending | null>(null);
  protected readonly step = signal(0);
  protected readonly slow = signal(false);
  protected readonly newMessage = signal(false);
  protected readonly sendFailed = signal(false);

  private readonly log = viewChild<ElementRef<HTMLElement>>('log');
  private readonly composer = viewChild(ChatComposerComponent);
  private timers: ReturnType<typeof setTimeout>[] = [];

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopWaiting());
    this.chat.chat().subscribe({
      next: (chat) => {
        this.personaName.set(chat.personaName);
        this.messages.set(chat.messages);
        const last = chat.messages.at(-1);
        if (last?.author === 'USER' && !chat.messages.some((m) => m.replyTo === last.id)) {
          this.pending.set({ status: 'failed', messageId: last.id });
        }
        this.load.set('ready');
        this.scrollToBottom();
      },
      error: () => this.load.set('failed'),
    });
  }

  protected isBusy(): boolean {
    return this.pending()?.status === 'waiting';
  }

  protected send(text: string): void {
    if (this.isBusy() || this.load() !== 'ready') {
      return;
    }
    this.sendFailed.set(false);
    this.startWaiting(null);
    this.scrollToBottom();
    this.chat.postMessage({ body: { text } }).subscribe({
      next: (question) => {
        this.append(question, true);
        this.answer(question.id);
      },
      error: () => {
        this.stopWaiting();
        this.pending.set(null);
        this.sendFailed.set(true);
        this.composer()?.restore(text);
      },
    });
  }

  /** Repeats only the answer call for the saved question. */
  protected tryAgain(): void {
    const pending = this.pending();
    if (pending?.status === 'failed') {
      this.startWaiting(pending.messageId);
      this.answer(pending.messageId);
    }
  }

  protected onScroll(): void {
    if (this.atBottom()) {
      this.newMessage.set(false);
    }
  }

  protected showNewMessage(): void {
    this.newMessage.set(false);
    this.scrollToBottom();
  }

  private answer(messageId: string): void {
    this.pending.set({ status: 'waiting', messageId });
    this.chat.answerMessage({ id: messageId }).subscribe({
      next: (answer) => {
        this.stopWaiting();
        this.pending.set(null);
        this.append(answer, false);
      },
      error: () => {
        this.stopWaiting();
        this.pending.set({ status: 'failed', messageId });
      },
    });
  }

  /** Adds a message without moving the reader: below the fold, a "New message" pill appears instead. */
  private append(message: ChatMessage, own: boolean): void {
    const follow = own || this.atBottom();
    this.messages.update((messages) => [...messages, message]);
    if (follow) {
      this.scrollToBottom();
    } else {
      this.newMessage.set(true);
    }
  }

  private startWaiting(messageId: string | null): void {
    this.stopWaiting();
    this.step.set(0);
    this.slow.set(false);
    this.pending.set({ status: 'waiting', messageId });
    this.timers.push(
      setInterval(
        () => this.step.update((s) => Math.min(s + 1, WAITING_STEPS.length - 1)),
        STEP_INTERVAL_MS,
      ),
      setTimeout(() => this.slow.set(true), SLOW_AFTER_MS),
    );
  }

  private stopWaiting(): void {
    for (const timer of this.timers) {
      clearInterval(timer);
      clearTimeout(timer);
    }
    this.timers = [];
  }

  private atBottom(): boolean {
    const log = this.log()?.nativeElement;
    return !log || log.scrollHeight - log.scrollTop - log.clientHeight < 24;
  }

  private scrollToBottom(): void {
    afterNextRender(
      {
        write: () => {
          const log = this.log()?.nativeElement;
          if (log) {
            log.scrollTop = log.scrollHeight;
          }
        },
      },
      { injector: this.injector },
    );
  }
}
