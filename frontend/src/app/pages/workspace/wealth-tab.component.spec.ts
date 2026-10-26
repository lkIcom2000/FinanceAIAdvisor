import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideEchartsCore } from 'ngx-echarts';
import { WealthOverview } from '../../api/models/wealth-overview';
import { httpTestingProviders } from '../../testing/session-testing';
import { WealthTabComponent } from './wealth-tab.component';

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

function figure(provId: string, value: string) {
  return { provId, label: provId, value, unit: 'EUR' as const };
}

function jonas(): WealthOverview {
  return {
    holdings: [
      {
        name: 'World equity ETF',
        assetClass: 'Equity ETF',
        units: '17.842300',
        value: figure('b000000000000001', '2004.76'),
      },
    ],
    savingsPlans: [
      {
        name: 'World equity ETF savings plan',
        monthlyAmount: figure('b000000000000002', '100.00'),
        dayOfMonth: 15,
      },
    ],
    projection: {
      provId: 'b000000000000004',
      years: 2,
      p10: figure('b000000000000003', '61234.56'),
      p50: figure('b000000000000004', '98765.43'),
      p90: figure('b000000000000005', '154321.99'),
      paidIn: figure('b000000000000006', '38004.76'),
      p50TodaysMoney: figure('b000000000000007', '54321.00'),
      startingValue: figure('b000000000000008', '2004.76'),
      monthlyContribution: figure('b000000000000009', '100.00'),
      points: [
        { year: 1, p10: '2900.00', p50: '3300.00', p90: '3700.00' },
        { year: 2, p10: '61234.56', p50: '98765.43', p90: '154321.99' },
      ],
      assumptions: ['Costs: fund costs of 0.2 % a year are deducted every month.'],
      wholeEuros: { p10: '61,235', p50: '98,765', p90: '154,322' },
    },
  };
}

describe('WealthTabComponent', () => {
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
      imports: [WealthTabComponent],
      providers: [...httpTestingProviders()],
    })
      .overrideComponent(WealthTabComponent, {
        set: { providers: [provideEchartsCore({ echarts: { init: () => new FakeChart() } })] },
      })
      .compileComponents();
  });

  async function render(body: WealthOverview) {
    const fixture = TestBed.createComponent(WealthTabComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.skeleton').length).toBe(3);
    TestBed.inject(HttpTestingController).expectOne('/api/overview/wealth').flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
    // ngx-echarts creates the chart in a timeout after the view is ready.
    await new Promise((resolve) => setTimeout(resolve, 10));
    await fixture.whenStable();
    return { fixture, element };
  }

  it('shows the holding and the savings plan with backend figures and a Why? on every figure', async () => {
    const { element } = await render(jonas());

    expect(element.querySelectorAll('.skeleton').length).toBe(0);
    expect(Array.from(element.querySelectorAll('.tile .label')).map((l) => l.textContent)).toEqual([
      'World equity ETF',
      'World equity ETF savings plan',
    ]);
    expect(
      Array.from(element.querySelectorAll('.figure-lg')).map((f) => f.textContent?.trim()),
    ).toEqual(['2,004.76 EUR', '100.00 EUR']);
    expect(element.querySelector('.holding .supporting')?.textContent).toBe(
      'Equity ETF, 17.842300 units',
    );
    expect(element.querySelector('.plan .supporting')?.textContent).toBe('a month, on day 15');
    // Holding, plan, projection, then p10, p50, p90, today's money and paid in.
    expect(element.querySelectorAll('app-why-link button').length).toBe(9);
    expect(element.textContent).not.toContain('b000000000000001');
    expect(element.textContent).not.toContain('Adjust');

    const open = vi.spyOn(TestBed.inject(MatDialog), 'open');
    element.querySelectorAll<HTMLButtonElement>('.figures app-why-link button')[0].click();
    expect(open.mock.calls[0][1]?.data).toEqual({ provId: 'b000000000000003' });
  });

  it('summarises the range in the exact sentence from the API amounts and lists the assumptions', async () => {
    const { element } = await render(jonas());

    expect(element.querySelector('.summary')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Between 61,235 and 154,322 EUR after 2 years. The middle outcome is 98,765 EUR. This is a range, not a promise.',
    );
    expect(element.querySelector('.assumptions-title')?.textContent).toBe('Assumptions');
    expect(element.querySelector('.assumptions li')?.textContent).toContain('0.2 % a year');
    const figures = Array.from(element.querySelectorAll('.figures .figure-row')).map((r) =>
      r.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(figures).toEqual([
      "Low end (1 in 10 chance it's lower) 61,234.56 EUR Why?",
      'Middle outcome 98,765.43 EUR Why?',
      "High end (1 in 10 chance it's higher) 154,321.99 EUR Why?",
      "Middle outcome in today's money 54,321.00 EUR Why?",
      'Total paid in 38,004.76 EUR Why?',
      'Value of your investments today 2,004.76 EUR Why?',
    ]);
  });

  it('draws the band with blue edges and a navy median under the exact legend', async () => {
    await render(jonas());

    const option = FakeChart.options.at(-1) as {
      legend: { data: string[] };
      series: { name: string; type: string; lineStyle?: { color: string } }[];
    };
    expect(option.legend.data).toEqual([
      'Middle outcome',
      "1 in 10 chance it's lower",
      "1 in 10 chance it's higher",
    ]);
    expect(option.series.map((s) => [s.name, s.type, s.lineStyle?.color])).toEqual([
      ['Range', 'custom', undefined],
      ["1 in 10 chance it's lower", 'line', '#0A5AE1'],
      ["1 in 10 chance it's higher", 'line', '#0A5AE1'],
      ['Middle outcome', 'line', '#000064'],
    ]);
  });

  it('lists every year in a visually hidden table', async () => {
    const { element } = await render(jonas());

    const rows = Array.from(element.querySelectorAll('table.visually-hidden tbody tr')).map((r) =>
      Array.from(r.children).map((c) => c.textContent?.trim()),
    );
    expect(rows).toEqual([
      ['1', '2,900.00 EUR', '3,300.00 EUR', '3,700.00 EUR'],
      ['2', '61,234.56 EUR', '98,765.43 EUR', '154,321.99 EUR'],
    ]);
  });

  it('shows the empty state without investments or savings plans', async () => {
    const { element } = await render({ holdings: [], savingsPlans: [] });

    expect(element.querySelector('.empty')?.textContent?.trim()).toBe(
      "You don't have any investments or savings plans yet.",
    );
    expect(element.querySelector('.chart')).toBeNull();
    expect(FakeChart.options.length).toBe(0);
  });

  it('says so when the overview cannot be loaded', async () => {
    const fixture = TestBed.createComponent(WealthTabComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/overview/wealth')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain("We couldn't load your wealth overview.");
  });
});
