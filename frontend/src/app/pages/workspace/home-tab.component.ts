import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith } from 'rxjs';
import { HomeOverview } from '../../api/models/home-overview';
import { InsuranceFlag } from '../../api/models/insurance-flag';
import { OverviewService } from '../../api/services/overview.service';
import { formatDate, formatShortDate } from '../../shared/dates';
import { FigurePipe, formatFigure } from '../../shared/figure.pipe';
import { WhyLinkComponent } from '../../shared/why-link.component';

type HomeState =
  { status: 'loading' } | { status: 'failed' } | { status: 'ready'; home: HomeOverview };

/** A flag as one plain sentence, with the policyholders of the contracts it cites. */
interface FlagLine {
  text: string;
  holders: string | null;
  provId: string;
}

/**
 * Home tab: the summary (the Liquidity tab's figures with the same keys), Notifications and rules (empty until
 * Epics 8 and 9) and "Your insurance". Every figure comes from the backend; this component only formats.
 */
@Component({
  selector: 'app-home-tab',
  imports: [FigurePipe, WhyLinkComponent],
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

  protected readonly formatDate = formatDate;

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
        const holders = flag.contractIds
          .map((id) => contracts.find((c) => c.id === id)?.policyholder)
          .filter((h): h is string => !!h);
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
