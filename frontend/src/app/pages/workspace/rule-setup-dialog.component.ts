import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { CheckView } from '../../api/models/check-view';
import { RuleRequest } from '../../api/models/rule-request';
import { RuleView } from '../../api/models/rule-view';
import { TemplateView } from '../../api/models/template-view';
import { RulesService } from '../../api/services/rules.service';

export const LOAD_FAILED = "We couldn't load the rules you can set up. Please try again later.";
export const NO_TEMPLATES =
  'A rule needs both a current account and an overnight deposit, so there is nothing to set up here.';
export const SAVE_FAILED = "We couldn't start this rule. Please try again later.";

type LimitKey = 'maxPerAction' | 'maxPerMonth' | 'floor';

/**
 * The rule setup dialog (FR-36, UX-DR23): (1) choose one of the two templates, (2) set the limits the Core suggests,
 * (3) check the full summary the backend builds from them, then "Start this rule". Closed with the started rule.
 * Every text comes from the backend; nothing opens a second dialog from here (no modal stack).
 */
@Component({
  selector: 'app-rule-setup-dialog',
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rule-setup-dialog.component.html',
  styleUrl: './rule-setup-dialog.component.scss',
})
export class RuleSetupDialogComponent {
  private readonly api = inject(RulesService);
  private readonly ref = inject<MatDialogRef<RuleSetupDialogComponent, RuleView>>(MatDialogRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  protected readonly templates = signal<TemplateView[] | undefined>(undefined);
  protected readonly loadError = signal<string | undefined>(undefined);
  protected readonly chosen = signal<TemplateView | undefined>(undefined);
  protected readonly limits = signal<Record<LimitKey, string>>({
    maxPerAction: '',
    maxPerMonth: '',
    floor: '',
  });
  protected readonly check = signal<CheckView | undefined>(undefined);
  protected readonly step = signal(0);
  protected readonly problem = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly stepLabels = ['Choose a rule', 'Set limits', 'Check and confirm'] as const;

  protected readonly fields: readonly { key: LimitKey; label: string }[] = [
    { key: 'maxPerAction', label: 'Most per transfer (EUR)' },
    { key: 'maxPerMonth', label: 'Most per month (EUR)' },
    { key: 'floor', label: 'Minimum balance of the account money comes from (EUR)' },
  ];

  constructor() {
    this.api.setup().subscribe({
      next: (t) => {
        this.templates.set(t);
        if (!t.length) {
          this.loadError.set(NO_TEMPLATES);
        }
        this.focusHeading();
      },
      error: () => {
        this.loadError.set(LOAD_FAILED);
        this.focusHeading();
      },
    });
  }

  protected choose(t: TemplateView): void {
    if (t.alreadyActive) {
      return;
    }
    if (this.chosen()?.template === t.template) {
      this.go(1);
      return;
    }
    this.chosen.set(t);
    this.limits.set({
      maxPerAction: t.maxPerAction.value,
      maxPerMonth: t.maxPerMonth.value,
      floor: t.floor.value,
    });
    this.check.set(undefined);
    this.go(1);
  }

  protected setLimit(key: LimitKey, value: string): void {
    this.limits.update((l) => ({ ...l, [key]: value.trim() }));
    this.problem.set(undefined);
  }

  /** Step 2 → 3: the backend checks the limits and writes the summary. */
  protected toCheck(): void {
    const body = this.request();
    if (!body || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api.check({ body }).subscribe({
      next: (c) => {
        this.busy.set(false);
        this.check.set(c);
        this.go(2);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.problem.set(this.problemText(e));
      },
    });
  }

  protected start(): void {
    const body = this.request();
    if (!body || this.busy()) {
      return;
    }
    this.setBusy(true);
    this.api.start({ body }).subscribe({
      next: (rule) => {
        this.setBusy(false);
        this.ref.close(rule);
      },
      error: (e: unknown) => {
        this.setBusy(false);
        this.problem.set(this.problemText(e));
      },
    });
  }

  protected go(step: number): void {
    this.problem.set(undefined);
    this.step.set(step);
    this.focusHeading();
  }

  private request(): RuleRequest | undefined {
    const t = this.chosen();
    return t ? { template: t.template, ...this.limits() } : undefined;
  }

  /** A 400 or 409 carries a customer sentence; anything else gets the generic one. */
  private problemText(e: unknown): string {
    if (e instanceof HttpErrorResponse && (e.status === 400 || e.status === 409)) {
      const detail = (e.error as { detail?: unknown } | null)?.detail;
      if (typeof detail === 'string' && detail) {
        return detail;
      }
    }
    return SAVE_FAILED;
  }

  /** While a rule is being started, the dialog cannot be dismissed. */
  private setBusy(busy: boolean): void {
    this.busy.set(busy);
    this.ref.disableClose = busy;
  }

  /** Moves focus to the title so screen readers announce the new step. */
  private focusHeading(): void {
    queueMicrotask(() => this.heading()?.nativeElement.focus());
  }
}
