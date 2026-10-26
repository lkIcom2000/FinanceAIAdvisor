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
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { FigureLinksService } from '../../shared/figure-links.service';
import { FigurePipe, fractionToPercent, percentToFraction } from '../../shared/figure.pipe';
import { openChecksDialog, openWhyDialog } from '../../shared/why-link.component';
import { ChatMessage } from '../../api/models/chat-message';
import { ChatProposalParameter } from '../../api/models/chat-proposal-parameter';
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

/** Inline Material icon paths (no icon font is bundled): one per outcome, plus the chip icon. */
export const ICON_PATHS: Record<string, string> = {
  calculate:
    'M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5.97 4.06L14.09 6l1.41 1.41L16.91 6l1.06 1.06-1.41 1.41 1.41 1.41-1.06 1.06-1.41-1.41-1.41 1.41-1.06-1.06 1.41-1.41-1.41-1.41zM6.25 7.72h5v1.5h-5v-1.5zM11.5 16h-2v2H8v-2H6v-1.5h2v-2h1.5v2h2V16zm6.5 1.25h-5v-1.5h5v1.5zm0-2.5h-5v-1.5h5v1.5z',
  recommend:
    'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm6 9.8c0 .17-.02.34-.07.5l-1.43 4.73c-.15.53-.6.97-1.16.97H9.83c-.83 0-1.33-.67-1.33-1.5v-5.33c0-.4.17-.77.43-1.04L12.5 6l.57.58c.15.15.24.37.24.6l-.02.21-.73 3.11h4.44c.82 0 1.5.68 1.5 1.5v-.2zM7 11H5.5v6H7v-6z',
  assignment_ind:
    'M19 3h-4.18C14.4 1.84 13.3 1 12 1s-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm0 4c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm6 12H6v-1.4c0-2 4-3.1 6-3.1s6 1.1 6 3.1V19z',
  do_not_disturb_on:
    'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z',
  report:
    'M15.73 3H8.27L3 8.27v7.46L8.27 21h7.46L21 15.73V8.27L15.73 3zM12 17.3c-.72 0-1.3-.58-1.3-1.3s.58-1.3 1.3-1.3 1.3.58 1.3 1.3-.58 1.3-1.3 1.3zm1-4.3h-2V7h2v6z',
  support_agent:
    'M21 12.22C21 6.73 16.74 3 12 3c-4.69 0-9 3.65-9 9.28-.6.34-1 .98-1 1.72v2c0 1.1.9 2 2 2h1v-6.1c0-3.87 3.13-7 7-7s7 3.13 7 7V19h-8v2h8c1.1 0 2-.9 2-2v-1.22c.59-.31 1-.92 1-1.62v-2.3c0-.7-.41-1.31-1-1.64zM9 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm6 0a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm3-1.97C17.52 8.18 15.04 6 12.05 6c-3.03 0-6.29 2.51-6.03 6.45 2.47-1.01 4.33-3.21 4.86-5.89 1.31 2.63 4 4.44 7.12 4.47z',
  menu_book:
    'M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z',
  chat: 'M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 9h12v2H6V9zm8 5H6v-2h8v2zm4-6H6V6h12v2z',
  link: 'M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z',
};

/** The source tag of a proposed value (UX-DR16). */
export const SOURCE_TAGS: Record<ChatProposalParameter['source'], string> = {
  QUESTION: 'From your question',
  ACCOUNT_DATA: 'From your account data',
  CHANGED: 'Changed by you',
};

/** What a value field accepts: a plain decimal with a point, as the backend sends it. */
const DECIMAL = /^-?\d+(\.\d+)?$/;

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
 * answer call. A parameter proposal is calculated only on Calculate (AD-24); a new question cancels it. Figure chips
 * open "Why?" and link to the Overview (FR-8).
 */
@Component({
  selector: 'app-chat-column',
  imports: [ChatComposerComponent, MatButtonModule, FigurePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-column.component.html',
  styleUrl: './chat-column.component.scss',
})
export class ChatColumnComponent {
  private readonly chat = inject(ChatService);
  private readonly injector = inject(Injector);
  private readonly dialog = inject(MatDialog);
  protected readonly links = inject(FigureLinksService);

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
  protected readonly sourceTags = SOURCE_TAGS;
  /** The proposal whose rows are fields ("Change values"), with the field texts by parameter key. */
  protected readonly editing = signal<{ messageId: string; values: Record<string, string> } | null>(null);
  /** Why the last Calculate of a proposal did not go through, shown on its card. */
  protected readonly calculateError = signal<{ messageId: string; text: string } | null>(null);

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
        if (chat.messages.some((m) => m.figures.length)) {
          this.links.refresh().subscribe();
        }
      },
      error: () => this.load.set('failed'),
    });
  }

  /**
   * The footer of an answer with final checks: "Checked · N checks passed" when all passed, "Checked · N of M checks
   * passed" when any failed (only a released draft at a future level can show that).
   */
  protected checkFooter(message: ChatMessage): string {
    const total = message.checks.length;
    const passed = message.checks.filter((c) => c.passed).length;
    if (passed < total) {
      return `Checked · ${passed} of ${total} checks passed`;
    }
    return `Checked · ${passed} ${passed === 1 ? 'check' : 'checks'} passed`;
  }

  /** The footer opens "Why?" on the answer's Checks section. */
  protected openChecks(message: ChatMessage): void {
    openChecksDialog(this.dialog, message.checks);
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
        // Only an accepted question cancels the open card; a failed send leaves it as the backend has it.
        this.cancelOpenProposals();
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

  /** The field text of a value: a share as a percentage ("0.04" as "4"), every other value as sent. */
  protected fieldText(p: ChatProposalParameter): string {
    return p.unit === 'FRACTION' ? fractionToPercent(p.value) : p.value;
  }

  protected startEditing(message: ChatMessage): void {
    const values: Record<string, string> = {};
    for (const p of message.proposal?.parameters ?? []) {
      values[p.key] = this.fieldText(p);
    }
    this.editing.set({ messageId: message.id, values });
  }

  protected edit(key: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim().replace(',', '.');
    this.editing.update((e) => (e ? { ...e, values: { ...e.values, [key]: value } } : e));
  }

  protected isEditing(message: ChatMessage): boolean {
    return this.editing()?.messageId === message.id;
  }

  /** Calculate: only now does the Core run. Changed fields go along, in the parameter's unit. */
  protected calculate(message: ChatMessage): void {
    if (this.isBusy() || !message.proposal) {
      return;
    }
    const parameters: { key: string; value: string }[] = [];
    const editing = this.isEditing(message) ? this.editing()!.values : {};
    for (const p of message.proposal.parameters) {
      const text = editing[p.key];
      if (text === undefined || text === this.fieldText(p)) {
        continue;
      }
      if (!DECIMAL.test(text)) {
        this.calculateError.set({ messageId: message.id, text: `Please enter a number for ${p.label}.` });
        return;
      }
      parameters.push({ key: p.key, value: p.unit === 'FRACTION' ? percentToFraction(text) : text });
    }
    this.calculateError.set(null);
    this.startWaiting(null);
    this.scrollToBottom();
    this.chat.calculate({ id: message.id, body: { parameters } }).subscribe({
      next: (answer) => {
        this.stopWaiting();
        this.pending.set(null);
        this.editing.set(null);
        this.setProposalStatus(message.id, 'CALCULATED');
        this.append(answer, true);
        this.announceFigures(answer);
      },
      error: (error: HttpErrorResponse) => {
        this.stopWaiting();
        this.pending.set(null);
        if (error.status === 409) {
          this.setProposalStatus(message.id, 'CANCELLED');
        } else {
          this.calculateError.set({
            messageId: message.id,
            text:
              error.status === 400 && typeof error.error?.detail === 'string'
                ? error.error.detail
                : "Something went wrong on our side, so I couldn't calculate this. Please try Calculate again.",
          });
        }
      },
    });
  }

  protected openWhy(provId: string): void {
    openWhyDialog(this.dialog, provId);
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
        this.announceFigures(answer);
      },
      error: () => {
        this.stopWaiting();
        this.pending.set({ status: 'failed', messageId });
      },
    });
  }

  /** A new answer with figures: refresh the Overview index, then ring or dot the matching elements. */
  private announceFigures(answer: ChatMessage): void {
    if (answer.figures.length) {
      this.links.refresh().subscribe(() => this.links.highlight(answer.figures.map((f) => f.provId)));
    }
  }

  /** A new question cancels every open proposal (the backend resets the dialog state in the same turn). */
  private cancelOpenProposals(): void {
    this.editing.set(null);
    this.calculateError.set(null);
    for (const m of this.messages()) {
      if (m.proposal?.status === 'OPEN') {
        this.setProposalStatus(m.id, 'CANCELLED');
      }
    }
  }

  private setProposalStatus(messageId: string, status: 'CALCULATED' | 'CANCELLED'): void {
    this.messages.update((messages) =>
      messages.map((m) =>
        m.id === messageId && m.proposal ? { ...m, proposal: { ...m.proposal, status } } : m,
      ),
    );
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
