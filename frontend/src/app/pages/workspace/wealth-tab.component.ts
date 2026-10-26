import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { catchError, map, of, startWith } from 'rxjs';
import { WealthOverview } from '../../api/models/wealth-overview';
import { OverviewService } from '../../api/services/overview.service';
import { FigurePipe, formatFigure } from '../../shared/figure.pipe';
import { WhyLinkComponent } from '../../shared/why-link.component';

type WealthState =
  { status: 'loading' } | { status: 'failed' } | { status: 'ready'; wealth: WealthOverview };

const NAVY = '#000064';
const ACTION_BLUE = '#0A5AE1';
const BAND = '#CCF1FF';
const GRID = '#C9DDE3';

/** The legend, exactly as the UX specification words it. */
export const MIDDLE = 'Middle outcome';
export const LOWER = "1 in 10 chance it's lower";
export const HIGHER = "1 in 10 chance it's higher";

/** Minimal shape of the ECharts custom-series API used to draw the range band. */
interface CustomSeriesApi {
  coord(value: [number, string]): number[];
}

/**
 * Wealth & Retirement tab: holdings, savings plans and the goal projection as a p10–p90 range. Every figure, point
 * and the whole-euro amounts in the summary come from the backend; this component only formats and draws.
 */
@Component({
  selector: 'app-wealth-tab',
  imports: [FigurePipe, WhyLinkComponent, NgxEchartsDirective],
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

  protected readonly legend = { middle: MIDDLE, lower: LOWER, higher: HIGHER };

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

  protected readonly chartOptions = computed<EChartsCoreOption | null>(() => {
    const s = this.state();
    if (s.status !== 'ready' || !s.wealth.projection) {
      return null;
    }
    const points = s.wealth.projection.points;
    const edge = (name: string, values: string[]) => ({
      name,
      type: 'line',
      showSymbol: false,
      data: points.map((p, i) => [p.year, values[i]]),
      lineStyle: { color: ACTION_BLUE, width: 1.5 },
      itemStyle: { color: ACTION_BLUE },
      z: 2,
    });
    return {
      animation: false,
      grid: { left: 80, right: 24, top: 40, bottom: 32 },
      legend: {
        top: 0,
        left: 0,
        data: [MIDDLE, LOWER, HIGHER],
        textStyle: { color: NAVY },
      },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'value',
        min: points[0].year,
        max: points[points.length - 1].year,
        minInterval: 1,
        axisLine: { lineStyle: { color: GRID } },
        axisLabel: { color: NAVY, formatter: (value: number) => `Year ${value}` },
        splitLine: { show: false },
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
          name: 'Range',
          type: 'custom',
          silent: true,
          itemStyle: { color: BAND },
          data: points.map((p) => [p.year, p.p10, p.p90]),
          renderItem: (params: { dataIndex: number }, api: CustomSeriesApi) => {
            if (params.dataIndex !== 0) {
              return null;
            }
            const upper = points.map((p) => api.coord([p.year, p.p90]));
            const lower = points.map((p) => api.coord([p.year, p.p10])).reverse();
            return {
              type: 'polygon',
              shape: { points: [...upper, ...lower] },
              style: { fill: BAND },
            };
          },
          z: 1,
        },
        edge(
          LOWER,
          points.map((p) => p.p10),
        ),
        edge(
          HIGHER,
          points.map((p) => p.p90),
        ),
        {
          name: MIDDLE,
          type: 'line',
          showSymbol: false,
          data: points.map((p) => [p.year, p.p50]),
          lineStyle: { color: NAVY, width: 2.5 },
          itemStyle: { color: NAVY },
          z: 3,
        },
      ],
    };
  });
}
