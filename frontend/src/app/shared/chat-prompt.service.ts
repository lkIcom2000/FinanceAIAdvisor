import { Injectable, signal } from '@angular/core';

/** A prepared question another part of the workspace asks the chat to send, e.g. "Ask Fine about this". */
@Injectable({ providedIn: 'root' })
export class ChatPromptService {
  /** The question waiting for the chat column; it clears it when taken. */
  readonly pending = signal<string | null>(null);

  ask(text: string): void {
    this.pending.set(text);
  }

  /** Returns the waiting question and clears it. */
  take(): string | null {
    const text = this.pending();
    this.pending.set(null);
    return text;
  }
}
