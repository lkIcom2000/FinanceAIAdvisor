import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { WhyDialogComponent, WhyDialogData } from './why-dialog.component';

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
    this.dialog.open<WhyDialogComponent, WhyDialogData>(WhyDialogComponent, {
      data: { provId: this.provId() },
      width: '560px',
      maxWidth: '90vw',
      restoreFocus: true,
    });
  }
}
