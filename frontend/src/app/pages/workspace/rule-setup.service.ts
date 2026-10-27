import { Injectable, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RuleView } from '../../api/models/rule-view';
import {
  RevokeRuleDialogComponent,
  RevokeRuleDialogResult,
} from './revoke-rule-dialog.component';
import { RuleSetupDialogComponent } from './rule-setup-dialog.component';

/**
 * Opens the rule setup dialog from Home or the chat's "Set up a rule" chip (FR-36), and the Revoke dialog from a rule
 * card (FR-38). Chat can only open the setup dialog, never start or stop a rule. `changed` counts started and stop
 * attempts so that "Your rules" reloads.
 */
@Injectable({ providedIn: 'root' })
export class RuleSetupService {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly changed = signal(0);

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
          this.changed.update((n) => n + 1);
          this.snackBar.open('Your rule is active.', undefined, { duration: 5000 });
        }
      });
  }

  revoke(rule: RuleView): void {
    this.dialog
      .open<RevokeRuleDialogComponent, RuleView, RevokeRuleDialogResult>(RevokeRuleDialogComponent, {
        data: rule,
        width: '480px',
        maxWidth: '90vw',
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.changed.update((n) => n + 1);
          const message = 'stopped' in result ? 'Your rule is stopped.' : result.failed;
          this.snackBar.open(message, undefined, { duration: 5000 });
        }
      });
  }
}
