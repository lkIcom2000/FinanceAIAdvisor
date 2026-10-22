import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { provideEchartsCore } from 'ngx-echarts';
import { LiquidityOverview } from '../../api/models/liquidity-overview';
import { httpTestingProviders } from '../../testing/session-testing';
import { LiquidityTabComponent } from './liquidity-tab.component';

/** Records the options ngx-echarts hands to the chart; jsdom has no layout to draw in. */
class FakeChart {
  static options: Record<string, unknown>[] = [];
  setOption(option: Record<string, unknown>): void {
    FakeChart.options.push(option);
  }
  on(): void {}
  off(): void {}
  resize(): void {}
  dispose(): void {}
  isDisposed(): boolean {
    return false;
  }
  showLoading(): void {}
  hideLoading(): void {}
}

function figure(provId: string, value: string, unit: 'EUR' | 'MONTHS' = 'EUR') {
  return { provId, label: provId, value, unit };
}

function lena(withShortfall = true): LiquidityOverview {
  return {
    currentAccount: figure('a000000000000001', '1974.00'),
    overnightDeposit: figure('a000000000000002', '2600.00'),
    liquidAssets: figure('a000000000000003', '4574.00'),
    variableSpending: figure('a000000000000004', '930.00'),
    emergencyFund: {
      current: figure('a000000000000005', '2600.00'),
      target: figure('a000000000000006', '5993.49'),
      targetMax: figure('a000000000000007', '11986.98'),
      months: figure('a000000000000008', '3', 'MONTHS'),
      monthlyExpenses: figure('a000000000000009', '1997.83'),
    },
    forecast: {
      provId: 'a00000000000000a',
      expectedEnd: figure('a00000000000000a', '1500.00'),
      points: [
        { date: '2026-10-31', low: '1300.00', expected: '1400.00', high: '1500.00' },
        { date: '2026-11-01', low: '-150.00', expected: '-90.00', high: '-30.00' },
        { date: '2027-01-15', low: '1400.00', expected: '1500.00', high: '1600.00' },
      ],
      assumptions: [
        'Variable spending of 930.00 EUR a month is spread evenly over the days of each month.',
      ],
    },
    ...(withShortfall
      ? {
          shortfall: {
            amount: figure('a00000000000000b', '-90.00'),
            date: '2026-11-01',
            wholeEuros: '-90',
          },
        }
      : {}),
    lowestBalance: {
      amount: figure('a00000000000000c', withShortfall ? '-90.00' : '120.00'),
      date: '2026-11-01',
      wholeEuros: withShortfall ? '-90' : '120',
    },
  };
}

describe('LiquidityTabComponent', () => {
  beforeEach(async () => {
    FakeChart.options = [];
    if (!('ResizeObserver' in window)) {
      (window as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe(): void {}
        unobserve(): void {}
        disconnect(): void {}
      };
    }
    await TestBed.configureTestingModule({
      imports: [LiquidityTabComponent],
      providers: [...httpTestingProviders()],
    })
      .overrideComponent(LiquidityTabComponent, {
        set: { providers: [provideEchartsCore({ echarts: { init: () => new FakeChart() } })] },
      })
      .compileComponents();
  });

  async function render(body: LiquidityOverview) {
    const fixture = TestBed.createComponent(LiquidityTabComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.skeleton').length).toBe(3);
    TestBed.inject(HttpTestingController).expectOne('/api/overview/liquidity').flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
    // ngx-echarts creates the chart in a timeout after the view is ready.
    await new Promise((resolve) => setTimeout(resolve, 10));
    await fixture.whenStable();
    return { fixture, element };
  }

  it('shows the three cards with backend figures and a Why? on each', async () => {
    const { element } = await render(lena());

    expect(element.querySelectorAll('.skeleton').length).toBe(0);
    const labels = Array.from(element.querySelectorAll('.tile .label')).map((l) => l.textContent);
    expect(labels).toEqual(['Current account', 'Overnight deposit', 'Emergency fund']);
    const figures = Array.from(element.querySelectorAll('.figure-lg')).map((f) =>
      f.textContent?.trim(),
    );
    expect(figures).toEqual(['1,974.00 EUR', '2,600.00 EUR', '2,600.00 EUR']);
    expect(
      element
        .querySelector('.tile:nth-child(3) .supporting')
        ?.textContent?.replace(/\s+/g, ' ')
        .trim(),
    ).toBe('of 5,993.49 EUR target (3 months of expenses) Why?');
    // Current, overnight, fund, target, forecast.
    expect(element.querySelectorAll('app-why-link button').length).toBe(5);
    expect(element.textContent).not.toContain('a000000000000001');
  });

  it('marks the shortfall with the risk callout and the marker that opens its Why?', async () => {
    const { element } = await render(lena());
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open');

    expect(element.querySelector('.risk')?.textContent).toBe(
      'Your forecast balance drops to −90 EUR on 1 Nov.',
    );
    const marker = element.querySelector<HTMLButtonElement>('button.marker');
    expect(marker?.textContent?.trim()).toBe('−90 EUR on 1 Nov');
    marker?.click();
    expect(open).toHaveBeenCalledOnce();
    expect(open.mock.calls[0][1]?.data).toEqual({ provId: 'a00000000000000b' });
  });

  it('opens the shortfall Why? from a click on the chart marker, and nothing from other chart clicks', async () => {
    const { fixture } = await render(lena());
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue(undefined as never);
    const chart = fixture.debugElement.query(By.css('[echarts]'));

    chart.triggerEventHandler('chartClick', { componentType: 'series' });
    expect(open).not.toHaveBeenCalled();

    chart.triggerEventHandler('chartClick', { componentType: 'markPoint' });
    expect(open).toHaveBeenCalledOnce();
    expect(open.mock.calls[0][1]?.data).toEqual({ provId: 'a00000000000000b' });
  });

  it('draws the expected line, the range band, the coral zero line and the marker', async () => {
    await render(lena());

    const option = FakeChart.options.at(-1) as {
      legend: { data: string[] };
      series: {
        name: string;
        type: string;
        markLine?: unknown;
        markPoint?: { label: { formatter: string } };
      }[];
    };
    expect(option.legend.data).toEqual(['Expected balance', 'Possible range']);
    expect(option.series.map((s) => [s.name, s.type])).toEqual([
      ['Possible range', 'custom'],
      ['Expected balance', 'line'],
    ]);
    expect(option.series[1].markLine).toEqual(
      expect.objectContaining({
        data: [{ yAxis: 0 }],
        lineStyle: expect.objectContaining({ color: '#FF5A46', type: 'dashed' }),
      }),
    );
    expect(option.series[1].markPoint?.label.formatter).toBe('−90 EUR on 1 Nov');
  });

  it('summarises the chart in one sentence and lists every day in a hidden table', async () => {
    const { element } = await render(lena());

    expect(element.querySelector('.summary')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Over the next three months, your expected balance is lowest at −90.00 EUR on 1 Nov 2026 and ends at 1,500.00 EUR on 15 Jan 2027.',
    );
    const rows = Array.from(element.querySelectorAll('table.visually-hidden tbody tr')).map((r) =>
      Array.from(r.children).map((c) => c.textContent?.trim()),
    );
    expect(rows).toEqual([
      ['31 Oct 2026', '1,300.00 EUR', '1,400.00 EUR', '1,500.00 EUR'],
      ['1 Nov 2026', '−150.00 EUR', '−90.00 EUR', '−30.00 EUR'],
      ['15 Jan 2027', '1,400.00 EUR', '1,500.00 EUR', '1,600.00 EUR'],
    ]);
    expect(element.querySelector('.assumptions li')?.textContent).toContain('930.00 EUR a month');
  });

  it('shows no callout and no marker without a shortfall', async () => {
    const { element } = await render(lena(false));

    expect(element.querySelector('.risk')).toBeNull();
    expect(element.querySelector('button.marker')).toBeNull();
    const option = FakeChart.options.at(-1) as { series: { markPoint?: unknown }[] };
    expect(option.series[1].markPoint).toBeUndefined();
  });

  it('says so when the overview cannot be loaded', async () => {
    const fixture = TestBed.createComponent(LiquidityTabComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/overview/liquidity')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain("We couldn't load your liquidity overview.");
  });
});
