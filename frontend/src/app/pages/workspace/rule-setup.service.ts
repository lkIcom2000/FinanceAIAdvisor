import { Injectable, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RuleView } from '../../api/models/rule-view';
import { RuleSetupDialogComponent } from './rule-setup-dialog.component';

/**
 * Opens the rule setup dialog from Home or the chat's "Set up a rule" chip (FR-36). Chat can only open the dialog,
 * never start a rule. `started` counts started rules so that "Your rules" reloads.
 */
@Injectable({ providedIn: 'root' })
export class RuleSetupService {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly started = signal(0);

  open(): void {
    this.dialog
      .open<RuleSetupDialogComponent, undefined, RuleView>(RuleSetupDialogComponent, {
        width: '560px',
        maxWidth: '90vw',
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe((rule) => {
        if (rule) {
          this.started.update((n) => n + 1);
          this.snackBar.open('Your rule is active.', undefined, { duration: 5000 });
        }
      });
  }
}
