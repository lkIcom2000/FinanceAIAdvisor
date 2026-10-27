import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { RuleView } from '../../api/models/rule-view';
import { RulesService } from '../../api/services/rules.service';

/** Closed with the stopped rule, or with the reason it could not be stopped; `undefined` for Keep it. */
export type RevokeRuleDialogResult = { stopped: RuleView } | { failed: string };

export const REVOKE_FAILED = "We couldn't stop this rule. Please try again later.";

/**
 * "Stop this rule?" (FR-38, UX-DR24): what stopping means, the last undo deadline if one is still open, and Keep it /
 * Stop this rule. The deadline text comes from the backend.
 */
@Component({
  selector: 'app-revoke-rule-dialog',
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './revoke-rule-dialog.component.html',
  styleUrl: './revoke-rule-dialog.component.scss',
})
export class RevokeRuleDialogComponent {
  private readonly api = inject(RulesService);
  private readonly ref =
    inject<MatDialogRef<RevokeRuleDialogComponent, RevokeRuleDialogResult>>(MatDialogRef);
  protected readonly rule = inject<RuleView>(MAT_DIALOG_DATA);
  protected readonly busy = signal(false);
  protected readonly consequence =
    "Fine won't move money under this rule anymore. Transfers already made stay as they are" +
    (this.rule.undoUntil ? `; you can still undo the last one until ${this.rule.undoUntil}.` : '.');

  protected stop(): void {
    this.busy.set(true);
    this.ref.disableClose = true;
    this.api.revoke({ id: this.rule.id }).subscribe({
      next: (stopped) => this.ref.close({ stopped }),
      error: (e: unknown) =>
        this.ref.close({
          failed:
            e instanceof HttpErrorResponse &&
            e.status === 409 &&
            typeof e.error?.detail === 'string'
              ? e.error.detail
              : REVOKE_FAILED,
        }),
    });
  }
}
