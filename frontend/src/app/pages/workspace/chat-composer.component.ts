import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/** Maximum message length (the backend rejects longer text). */
export const MAX_MESSAGE_LENGTH = 1000;
/** The character counter shows from this length on. */
export const COUNTER_FROM = 800;

/**
 * The chat composer: Enter sends, Shift+Enter adds a new line, at most 1,000 characters with a counter from 800.
 * Disabled while a turn is in progress (one turn at a time).
 */
@Component({
  selector: 'app-chat-composer',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="composer" (submit)="$event.preventDefault(); submit()">
      <label class="sr-only" for="fine-chat-input">Your message to Fine</label>
      <textarea
        id="fine-chat-input"
        rows="2"
        [value]="text()"
        [attr.maxlength]="max"
        [disabled]="disabled() || busy()"
        [placeholder]="busy() ? 'Fine is checking the answer…' : 'Ask Fine a question'"
        (input)="onInput($event)"
        (keydown)="onKeydown($event)"
      ></textarea>
      <div class="row">
        @if (showCounter()) {
          <span class="counter">{{ text().length.toLocaleString('en') }} / 1,000</span>
        }
        <button mat-flat-button type="submit" [disabled]="!canSend()">Send</button>
      </div>
    </form>
  `,
  styles: `
    .composer {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px 16px 16px;
      border-top: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface);
    }
    textarea {
      resize: none;
      padding: 10px 12px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: 8px;
      font: var(--fine-body);
      color: var(--mat-sys-on-surface);
      background: var(--mat-sys-surface);
    }
    textarea:disabled {
      background: var(--fine-ice);
    }
    .row {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
    }
    .counter {
      font: var(--fine-body-sm);
      color: var(--mat-sys-on-surface-variant);
      font-variant-numeric: tabular-nums;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class ChatComposerComponent {
  readonly disabled = input(false);
  /** A turn is in progress. */
  readonly busy = input(false);
  readonly send = output<string>();

  protected readonly max = MAX_MESSAGE_LENGTH;
  protected readonly text = signal('');
  protected readonly showCounter = computed(() => this.text().length >= COUNTER_FROM);
  protected readonly canSend = computed(
    () => !this.disabled() && !this.busy() && this.text().trim().length > 0,
  );

  /** Puts text back into the field, e.g. after the message could not be sent. */
  restore(text: string): void {
    this.text.set(text.slice(0, MAX_MESSAGE_LENGTH));
  }

  protected onInput(event: Event): void {
    const field = event.target as HTMLTextAreaElement;
    if (field.value.length > MAX_MESSAGE_LENGTH) {
      field.value = field.value.slice(0, MAX_MESSAGE_LENGTH);
    }
    this.text.set(field.value);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.submit();
    }
  }

  protected submit(): void {
    if (!this.canSend()) {
      return;
    }
    const text = this.text().trim();
    this.text.set('');
    this.send.emit(text);
  }
}
