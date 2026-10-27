import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TransferReview } from '../../api/models/transfer-review';
import { ActionsService } from '../../api/services/actions.service';
import { FigurePipe } from '../../shared/figure.pipe';

export interface ConfirmActionDialogData {
  actionId: string;
}

/** Closed with this after a successful confirmation; `undefined` otherwise. */
export interface ConfirmActionDialogResult {
  amountText: string;
}

/** Shown when the Action is no longer a transfer waiting for the OK, or the request failed. */
export const NOT_WAITING = 'This transfer is no longer waiting for your OK.';
export const LOAD_FAILED = "We couldn't load this transfer. Please try again later.";
/** A confirmation without an answer may still have been booked: the refreshed list tells. */
export const CONFIRM_FAILED =
  "We couldn't tell whether this transfer was made. Please check your notifications before trying again.";

/**
 * The L2 confirmation dialog (FR-39, FR-40, UX-DR20): the exact transfer as a sentence, both accounts before and
 * after, the plain reason with "Why?", and Confirm / Not now. A refused re-check shows the reason and only Close.
 * Every text and figure comes from the backend; this component only formats.
 */
@Component({
  selector: 'app-confirm-action-dialog',
  imports: [MatDialogModule, MatButtonModule, FigurePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './confirm-action-dialog.component.html',
  styleUrl: './confirm-action-dialog.component.scss',
})
export class ConfirmActionDialogComponent {
  private readonly api = inject(ActionsService);
  private readonly ref =
    inject<MatDialogRef<ConfirmActionDialogComponent, ConfirmActionDialogResult>>(MatDialogRef);
  private readonly data = inject<ConfirmActionDialogData>(MAT_DIALOG_DATA);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  protected readonly review = signal<TransferReview | undefined>(undefined);
  /** A refusal or error: the dialog then offers only Close. */
  protected readonly stopped = signal<{ reason: string; why?: string } | undefined>(undefined);
  protected readonly busy = signal(false);
  protected readonly whyOpen = signal(false);

  constructor() {
    this.api.review({ id: this.data.actionId }).subscribe({
      next: (r) => {
        this.review.set(r);
        this.focusHeading();
      },
      error: (e: unknown) => {
        this.stopped.set({ reason: this.errorText(e) });
        this.focusHeading();
      },
    });
  }

  protected confirm(): void {
    const review = this.review();
    if (!review || this.busy()) {
      return;
    }
    this.setBusy(true);
    this.api.confirm({ id: this.data.actionId }).subscribe({
      next: (result) => {
        this.setBusy(false);
        if (result.done) {
          this.ref.close({ amountText: result.amountText });
        } else {
          this.whyOpen.set(false);
          this.stopped.set({ reason: result.reason ?? '', why: result.why });
          this.focusHeading();
        }
      },
      error: (e: unknown) => {
        this.setBusy(false);
        this.whyOpen.set(false);
        this.stopped.set({
          reason:
            e instanceof HttpErrorResponse && (e.status === 404 || e.status === 409)
              ? NOT_WAITING
              : CONFIRM_FAILED,
        });
        this.focusHeading();
      },
    });
  }

  protected toggleWhy(): void {
    this.whyOpen.update((open) => !open);
  }

  /** While the confirmation runs, the dialog cannot be dismissed: its answer decides what the user is told. */
  private setBusy(busy: boolean): void {
    this.busy.set(busy);
    this.ref.disableClose = busy;
  }

  private errorText(e: unknown): string {
    return e instanceof HttpErrorResponse && (e.status === 404 || e.status === 409)
      ? NOT_WAITING
      : LOAD_FAILED;
  }

  /** Moves focus to the title so screen readers announce the new content. */
  private focusHeading(): void {
    queueMicrotask(() => this.heading()?.nativeElement.focus());
  }
}
