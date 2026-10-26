import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { FigurePipe, formatFigure } from './figure.pipe';

const NAVY = '#000064';
const ACTION_BLUE = '#0A5AE1';
const BAND = '#CCF1FF';
const GRID = '#C9DDE3';

/** The legend, exactly as the UX specification words it. */
export const MIDDLE = 'Middle outcome';
export const LOWER = "1 in 10 chance it's lower";
export const HIGHER = "1 in 10 chance it's higher";

/** One year of a range: the backend's percentiles as decimal strings (EUR). */
export interface RangePoint {
  year: number;
  p10: string;
  p50: string;
  p90: string;
}

/** Minimal shape of the ECharts custom-series API used to draw the range band. */
interface CustomSeriesApi {
  coord(value: [number, string]): number[];
}

/**
 * The band chart of a projection (UX-DR17): light-cyan p10–p90 band with blue edges, navy median and the spelled-out
 * legend, plus the same points as a visually hidden table. It only draws the backend's points. The host provides
 * ngx-echarts (`provideEchartsCore`), so tests can swap the chart.
 */
@Component({
  selector: 'app-range-chart',
  imports: [NgxEchartsDirective, FigurePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="chart"
      echarts
      aria-hidden="true"
      [style.height.px]="height()"
      [options]="options()"
      [initOpts]="{ renderer: 'svg' }"
    ></div>
    <table class="visually-hidden">
      <caption>
        {{ caption() }}
      </caption>
      <thead>
        <tr>
          <th scope="col">Year</th>
          <th scope="col">{{ legend.lower }}</th>
          <th scope="col">{{ legend.middle }}</th>
          <th scope="col">{{ legend.higher }}</th>
        </tr>
      </thead>
      <tbody>
        @for (p of points(); track p.year) {
          <tr>
            <th scope="row">{{ p.year }}</th>
            <td>{{ p.p10 | figure: 'EUR' }}</td>
            <td>{{ p.p50 | figure: 'EUR' }}</td>
            <td>{{ p.p90 | figure: 'EUR' }}</td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    :host {
      display: block;
    }
    .chart {
      margin-top: 8px;
    }
  `,
})
export class RangeChartComponent {
  readonly points = input.required<RangePoint[]>();
  readonly caption = input.required<string>();
  readonly height = input(320);

  protected readonly legend = { middle: MIDDLE, lower: LOWER, higher: HIGHER };

  protected readonly options = computed<EChartsCoreOption>(() => {
    const points = this.points();
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
        min: points[0]?.year,
        max: points[points.length - 1]?.year,
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
