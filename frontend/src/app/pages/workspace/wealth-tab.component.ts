import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { provideEchartsCore } from 'ngx-echarts';
import { catchError, map, of, startWith } from 'rxjs';
import { WealthOverview } from '../../api/models/wealth-overview';
import { OverviewService } from '../../api/services/overview.service';
import { FigurePipe } from '../../shared/figure.pipe';
import { HIGHER, LOWER, MIDDLE, RangeChartComponent } from '../../shared/range-chart.component';
import { WhyLinkComponent } from '../../shared/why-link.component';

type WealthState =
  { status: 'loading' } | { status: 'failed' } | { status: 'ready'; wealth: WealthOverview };

/**
 * Wealth & Retirement tab: holdings, savings plans and the goal projection as a p10–p90 range. Every figure, point
 * and the whole-euro amounts in the summary come from the backend; this component only formats and draws.
 */
@Component({
  selector: 'app-wealth-tab',
  imports: [FigurePipe, WhyLinkComponent, RangeChartComponent],
  providers: [
    provideEchartsCore({
      echarts: () => import('../../shared/echarts-setup').then((m) => m.echarts),
    }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './wealth-tab.component.html',
  styleUrl: './wealth-tab.component.scss',
})
export class WealthTabComponent {
  protected readonly state = toSignal(
    inject(OverviewService)
      .wealth()
      .pipe(
        map((wealth): WealthState => ({ status: 'ready', wealth })),
        catchError(() => of<WealthState>({ status: 'failed' })),
        startWith<WealthState>({ status: 'loading' }),
      ),
    { requireSync: true },
  );

  /** The projection's figures, each with its "Why?"; the starting value is the one chat calculations use. */
  protected readonly figureRows = computed(() => {
    const s = this.state();
    const pr = s.status === 'ready' ? s.wealth.projection : undefined;
    if (!pr) {
      return [];
    }
    return [
      { name: `Low end (${LOWER})`, figure: pr.p10 },
      { name: MIDDLE, figure: pr.p50 },
      { name: `High end (${HIGHER})`, figure: pr.p90 },
      { name: "Middle outcome in today's money", figure: pr.p50TodaysMoney },
      { name: 'Total paid in', figure: pr.paidIn },
      { name: 'Value of your investments today', figure: pr.startingValue },
    ];
  });
}
