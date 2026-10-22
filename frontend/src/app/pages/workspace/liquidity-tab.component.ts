import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import type { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { catchError, map, of, startWith } from 'rxjs';
import { LiquidityOverview } from '../../api/models/liquidity-overview';
import { OverviewService } from '../../api/services/overview.service';
import { formatDate, formatShortDate } from '../../shared/dates';
import { FigurePipe, formatFigure } from '../../shared/figure.pipe';
import { WhyLinkComponent, openWhyDialog } from '../../shared/why-link.component';

type LiquidityState =
  { status: 'loading' } | { status: 'failed' } | { status: 'ready'; liquidity: LiquidityOverview };

const NAVY = '#000064';
const ACTION_BLUE = '#0A5AE1';
const BAND = '#CCF1FF';
const CORAL = '#FF5A46';
const GRID = '#C9DDE3';

/** Minimal shape of the ECharts custom-series API used to draw the range band. */
interface CustomSeriesApi {
  coord(value: [string, string]): number[];
}

/**
 * Liquidity tab: balances, emergency fund and the three-month cash-flow forecast. Every figure, point and sentence
 * value comes from the backend; this component only formats and draws (no arithmetic on money).
 */
@Component({
  selector: 'app-liquidity-tab',
  imports: [FigurePipe, WhyLinkComponent, NgxEchartsDirective],
  providers: [
    provideEchartsCore({
      echarts: () => import('../../shared/echarts-setup').then((m) => m.echarts),
    }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './liquidity-tab.component.html',
  styleUrl: './liquidity-tab.component.scss',
})
export class LiquidityTabComponent {
  private readonly dialog = inject(MatDialog);

  protected readonly state = toSignal(
    inject(OverviewService)
      .liquidity()
      .pipe(
        map((liquidity): LiquidityState => ({ status: 'ready', liquidity })),
        catchError(() => of<LiquidityState>({ status: 'failed' })),
        startWith<LiquidityState>({ status: 'loading' }),
      ),
    { requireSync: true },
  );

  protected readonly formatDate = formatDate;

  /** "−90 EUR on 1 Nov": the backend's whole-euro amount, never rounded here. */
  protected readonly marker = computed(() => {
    const s = this.state();
    if (s.status !== 'ready' || !s.liquidity.shortfall) {
      return null;
    }
    const shortfall = s.liquidity.shortfall;
    return {
      text: `${formatFigure(shortfall.wholeEuros, 'EUR')} on ${formatShortDate(shortfall.date)}`,
      callout: `Your forecast balance drops to ${formatFigure(shortfall.wholeEuros, 'EUR')} on ${formatShortDate(shortfall.date)}.`,
      provId: shortfall.amount.provId,
    };
  });

  protected readonly chartOptions = computed<EChartsCoreOption | null>(() => {
    const s = this.state();
    if (s.status !== 'ready') {
      return null;
    }
    const { forecast, shortfall } = s.liquidity;
    const points = forecast.points;
    const marker = this.marker();
    return {
      animation: false,
      grid: { left: 64, right: 24, top: 40, bottom: 32 },
      legend: {
        top: 0,
        left: 0,
        data: ['Expected balance', 'Possible range'],
        textStyle: { color: NAVY },
      },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: GRID } },
        axisLabel: { color: NAVY },
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          color: NAVY,
          formatter: (value: number) => formatFigure(String(value), 'EUR'),
        },
        splitLine: { lineStyle: { color: GRID } },
      },
      series: [
        {
          name: 'Possible range',
          type: 'custom',
          silent: true,
          itemStyle: { color: BAND },
          data: points.map((p) => [p.date, p.low, p.high]),
          renderItem: (params: { dataIndex: number }, api: CustomSeriesApi) => {
            if (params.dataIndex !== 0) {
              return null;
            }
            const upper = points.map((p) => api.coord([p.date, p.high]));
            const lower = points.map((p) => api.coord([p.date, p.low])).reverse();
            return {
              type: 'polygon',
              shape: { points: [...upper, ...lower] },
              style: { fill: BAND },
            };
          },
          z: 1,
        },
        {
          name: 'Expected balance',
          type: 'line',
          showSymbol: false,
          data: points.map((p) => [p.date, p.expected]),
          lineStyle: { color: ACTION_BLUE, width: 2 },
          itemStyle: { color: ACTION_BLUE },
          z: 3,
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            lineStyle: { color: CORAL, type: 'dashed', width: 1.5 },
            data: [{ yAxis: 0 }],
          },
          markPoint:
            shortfall && marker
              ? {
                  symbol: 'circle',
                  symbolSize: 10,
                  itemStyle: { color: CORAL },
                  label: { show: true, position: 'bottom', color: NAVY, formatter: marker.text },
                  data: [{ name: 'shortfall', coord: [shortfall.date, shortfall.amount.value] }],
                }
              : undefined,
        },
      ],
    };
  });

  /** A click on the chart's shortfall marker opens its "Why?". */
  protected onChartClick(event: { componentType?: string }): void {
    const marker = this.marker();
    if (event.componentType === 'markPoint' && marker) {
      openWhyDialog(this.dialog, marker.provId);
    }
  }

  protected openShortfallWhy(): void {
    const marker = this.marker();
    if (marker) {
      openWhyDialog(this.dialog, marker.provId);
    }
  }
}
