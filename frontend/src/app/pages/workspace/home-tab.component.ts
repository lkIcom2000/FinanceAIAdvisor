import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { HomeOverview } from '../../api/models/home-overview';
import { InsuranceFlag } from '../../api/models/insurance-flag';
import { NeedsStatement } from '../../api/models/needs-statement';
import { ChatService } from '../../api/services/chat.service';
import { OverviewService } from '../../api/services/overview.service';
import { RulesService } from '../../api/services/rules.service';
import { RuleView } from '../../api/models/rule-view';
import { formatDate, formatShortDate } from '../../shared/dates';
import { FigurePipe, formatFigure } from '../../shared/figure.pipe';
import { WhyLinkComponent } from '../../shared/why-link.component';
import { NotificationListComponent } from './notification-list.component';
import { RuleSetupService } from './rule-setup.service';

type HomeState =
  { status: 'loading' } | { status: 'failed' } | { status: 'ready'; home: HomeOverview };

/** A flag as one plain sentence, with the policyholders of the contracts it cites. */
interface FlagLine {
  text: string;
  holders: string | null;
  provId: string;
}

/**
 * Home tab: the summary (the Liquidity tab's figures with the same keys), Notifications, "Your rules" with Set up a rule and "Your insurance" with the demands-and-needs statement once the chat made one. Every figure comes from the backend; this component only formats.
 */
@Component({
  selector: 'app-home-tab',
  imports: [FigurePipe, MatButtonModule, NotificationListComponent, WhyLinkComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home-tab.component.html',
  styleUrl: './home-tab.component.scss',
})
export class HomeTabComponent {
  protected readonly state = toSignal(
    inject(OverviewService)
      .home()
      .pipe(
        map((home): HomeState => ({ status: 'ready', home })),
        catchError(() => of<HomeState>({ status: 'failed' })),
        startWith<HomeState>({ status: 'loading' }),
      ),
    { requireSync: true },
  );

  /** UX-DR44: the latest demands-and-needs statement from the chat; none (204) or unavailable shows nothing. */
  protected readonly needs = toSignal(
    inject(ChatService)
      .needsStatement()
      .pipe(catchError(() => of<NeedsStatement | null>(null))),
    { initialValue: null },
  );

  private readonly ruleSetup = inject(RuleSetupService);
  private readonly rulesApi = inject(RulesService);

  /** All rules, reloaded after a rule was started or stopped; `undefined` while loading. */
  protected readonly rules = toSignal<RuleView[] | 'failed' | undefined>(
    toObservable(this.ruleSetup.changed).pipe(
      switchMap(() => this.rulesApi.rules().pipe(catchError(() => of<'failed'>('failed')))),
    ),
    { initialValue: undefined },
  );

  protected readonly activeRules = computed(() => {
    const rs = this.rules();
    return Array.isArray(rs) ? rs.filter((r) => !r.stoppedOn) : [];
  });

  /** The stopped rules for the collapsed list, in the backend's order (latest stop first). */
  protected readonly stoppedRules = computed(() => {
    const rs = this.rules();
    return Array.isArray(rs) ? rs.filter((r) => r.stoppedOn) : [];
  });

  protected readonly formatDate = formatDate;

  protected setupRule(): void {
    this.ruleSetup.open();
  }

  protected revokeRule(rule: RuleView): void {
    this.ruleSetup.revoke(rule);
  }

  protected actionsLine(count: number): string {
    if (count === 0) {
      return 'No actions under this rule yet';
    }
    return count === 1 ? '1 action under this rule' : `${count} actions under this rule`;
  }

  /** The Liquidity tab's risk callout: the backend's whole-euro amount, never rounded here. */
  protected readonly callout = computed(() => {
    const s = this.state();
    if (s.status !== 'ready' || !s.home.shortfall) {
      return null;
    }
    const shortfall = s.home.shortfall;
    return `Your forecast balance drops to ${formatFigure(shortfall.wholeEuros, 'EUR')} on ${formatShortDate(shortfall.date)}.`;
  });

  protected readonly flagLines = computed<FlagLine[]>(() => {
    const s = this.state();
    if (s.status !== 'ready') {
      return [];
    }
    const { contracts, flags } = s.home.insurance;
    return flags.map((flag: InsuranceFlag) => {
      if (flag.kind === 'DUPLICATE_COVERAGE') {
        const holders = [
          ...new Set(
            flag.contractIds
              .map((id) => contracts.find((c) => c.id === id)?.policyholder)
              .filter((h): h is string => !!h),
          ),
        ];
        return {
          text: `You may be paying twice for ${flag.coverage} insurance`,
          holders: holders.length ? `Policies held by ${joinNames(holders)}` : null,
          provId: flag.provId,
        };
      }
      return {
        text: `You don't have ${flag.coverage} insurance.`,
        holders: null,
        provId: flag.provId,
      };
    });
  });
}

/** "A", "A and B", "A, B and C". */
function joinNames(names: string[]): string {
  return names.length < 2
    ? names.join('')
    : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}
