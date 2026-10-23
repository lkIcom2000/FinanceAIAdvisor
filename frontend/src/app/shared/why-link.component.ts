import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ChatCheck } from '../api/models/chat-check';
import { WhyDialogComponent, WhyDialogData } from './why-dialog.component';

/** Opens the "Why?" dialog for a figure; focus returns to the element that had it when the dialog closes. */
export function openWhyDialog(dialog: MatDialog, provId: string): void {
  openDialog(dialog, { provId });
}

/** Opens the "Why?" dialog on the Checks section of an answer. */
export function openChecksDialog(dialog: MatDialog, checks: ChatCheck[]): void {
  openDialog(dialog, { checks });
}

function openDialog(dialog: MatDialog, data: WhyDialogData): void {
  dialog.open<WhyDialogComponent, WhyDialogData>(WhyDialogComponent, {
    data,
    width: '560px',
    maxWidth: '90vw',
    restoreFocus: true,
  });
}

/** The "Why?" link next to a figure. The dialog returns focus to this trigger when it closes (Esc or Close). */
@Component({
  selector: 'app-why-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button type="button" class="why" (click)="open()">Why?</button>`,
  styles: `
    .why {
      min-height: 24px;
      padding: 0 4px;
      border: 0;
      background: none;
      color: var(--mat-sys-primary);
      font: var(--fine-label);
      cursor: pointer;
      text-decoration: underline;
    }
  `,
})
export class WhyLinkComponent {
  private readonly dialog = inject(MatDialog);

  readonly provId = input.required<string>();

  protected open(): void {
    openWhyDialog(this.dialog, this.provId());
  }
}
