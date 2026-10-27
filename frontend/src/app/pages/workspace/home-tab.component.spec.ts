import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { HomeOverview } from '../../api/models/home-overview';
import { RuleView } from '../../api/models/rule-view';
import { NeedsStatement } from '../../api/models/needs-statement';
import { httpTestingProviders } from '../../testing/session-testing';
import { HomeTabComponent } from './home-tab.component';
import { RuleSetupService } from './rule-setup.service';

function figure(provId: string, value: string, unit: 'EUR' | 'MONTHS' | 'COUNT' = 'EUR') {
  return { provId, label: provId, value, unit };
}

const JONAS_OWN = '1f2ac9ef-20a0-3c21-9a27-eb5af4446d06';
const JONAS_FAMILY = 'f477676a-c999-3809-8036-31700e910779';

function home(overrides: Partial<HomeOverview> = {}): HomeOverview {
  return {
    currentAccount: figure('a000000000000001', '1974.00'),
    lowestBalance: {
      amount: figure('a000000000000002', '-90.00'),
      date: '2026-11-01',
      wholeEuros: '-90',
    },
    shortfall: {
      amount: figure('a000000000000003', '-90.00'),
      date: '2026-11-01',
      wholeEuros: '-90',
    },
    emergencyFund: {
      current: figure('a000000000000004', '5700.00'),
      target: figure('a000000000000005', '5993.49'),
      targetMax: figure('a000000000000006', '11986.98'),
      months: figure('a000000000000007', '3', 'MONTHS'),
      monthlyExpenses: figure('a000000000000008', '1997.83'),
    },
    insurance: {
      contracts: [
        {
          id: JONAS_OWN,
          type: 'Private liability insurance',
          policyholder: 'Jonas',
          coInsured: false,
          premium: figure('a000000000000009', '48.00'),
          paymentInterval: 'yearly',
          nextDueDate: '2027-02-01',
        },
        {
          id: JONAS_FAMILY,
          type: 'Private liability insurance',
          policyholder: 'Parents of Jonas',
          coInsured: true,
        },
      ],
      flags: [
        {
          kind: 'DUPLICATE_COVERAGE',
          coverage: 'liability',
          provId: 'a00000000000000a',
          contractIds: [JONAS_OWN, JONAS_FAMILY],
        },
        {
          kind: 'MISSING_COVER',
          coverage: 'occupational disability',
          provId: 'a00000000000000b',
          contractIds: [],
        },
      ],
    },
    ...overrides,
  };
}

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('HomeTabComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeTabComponent],
      providers: [...httpTestingProviders()],
    }).compileComponents();
  });

  async function render(
    body: HomeOverview,
    needs: NeedsStatement | null = null,
    rules: RuleView[] = [],
  ) {
    const fixture = TestBed.createComponent(HomeTabComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/overview/home').flush(body);
    http.expectOne('/api/rules').flush(rules);
    http
      .expectOne('/api/chat/needs-statement')
      .flush(needs, needs ? {} : { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows three skeletons while loading', () => {
    const fixture = TestBed.createComponent(HomeTabComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.skeleton').length).toBe(3);
    TestBed.inject(HttpTestingController).expectOne('/api/overview/home');
  });

  it('shows the three summary figures with Why? and the shortfall callout', async () => {
    const element = await render(home());
    const tiles = Array.from(element.querySelectorAll('.tile'));
    expect(tiles.map((t) => text(t.querySelector('.label')))).toEqual([
      'Current account',
      'Lowest balance in the next three months',
      'Emergency fund',
    ]);
    expect(tiles.map((t) => text(t.querySelector('.figure-lg')))).toEqual([
      '1,974.00 EUR',
      '−90.00 EUR',
      '5,700.00 EUR',
    ]);
    expect(tiles.every((t) => t.querySelector('app-why-link button'))).toBe(true);
    expect(tiles[1].querySelector('.figure-lg')?.classList).toContain('negative');
    expect(text(tiles[1].querySelector('.supporting'))).toBe('on 1 Nov 2026');
    expect(text(tiles[1].querySelector('.risk'))).toBe(
      'Your forecast balance drops to −90 EUR on 1 Nov.',
    );
    expect(text(tiles[2].querySelector('.supporting'))).toContain(
      'of 5,993.49 EUR target (3 months of expenses)',
    );
    expect(element.textContent).not.toContain('a000000000000001');
  });

  it('shows no callout without a shortfall', async () => {
    const element = await render(home({ shortfall: undefined }));
    expect(element.querySelector('.risk')).toBeNull();
  });

  it('shows the Notifications and rules empty states and the Set up a rule button', async () => {
    const element = await render(home());
    const sections = Array.from(element.querySelectorAll('.section'));
    expect(sections.map((s) => text(s.querySelector('.label')))).toEqual([
      'Notifications',
      'Your rules',
      'Your insurance',
    ]);
    expect(text(sections[0].querySelector('.empty'))).toBe(
      'Nothing needs your attention right now. Fine checks your accounts regularly and will let you know.',
    );
    expect(text(sections[1].querySelector('.empty'))).toBe(
      "You haven't set up any rules. With a rule, Fine can take care of small things for you, like keeping your current account above zero.",
    );
    const open = vi.spyOn(TestBed.inject(RuleSetupService), 'open').mockReturnValue(undefined);
    const setup = sections[1].querySelector('.setup-rule') as HTMLButtonElement;
    expect(text(setup)).toBe('Set up a rule');
    setup.click();
    expect(open).toHaveBeenCalled();
  });

  it("shows Lena's rule as a card with its summary, limits, start date and Revoke", async () => {
    const summary =
      'Keep my current account above 0 EUR, using my overnight deposit, but never let the overnight deposit fall below 2,000 EUR';
    const element = await render(home(), null, [
      {
        id: 'r1',
        template: 'SAFETY_CUSHION',
        summary,
        fromName: 'Overnight deposit',
        toName: 'Current account',
        limits: { maxPerAction: '300.00', maxPerMonth: '600.00', floor: '2000.00' },
        activeSince: '2026-10-03',
        actionCount: 0,
      },
    ]);
    const card = element.querySelector('.rule') as HTMLElement;
    expect(text(card.querySelector('.rule-title'))).toBe(summary);
    expect(Array.from(card.querySelectorAll('.bounds div')).map(text)).toEqual([
      'From Overnight deposit',
      'To Current account',
      'Most per transfer 300.00 EUR',
      'Most per month 600.00 EUR',
      'Minimum balance of Overnight deposit 2,000.00 EUR',
    ]);
    expect(text(card.querySelector('.supporting'))).toBe('No actions under this rule yet');
    expect(text(card.querySelector('.rule-status span'))).toContain('Active since');
    expect(text(card.querySelector('.revoke'))).toBe('Revoke');
    expect(element.querySelector('.section .empty + .empty')).toBeNull();
    expect(card.textContent?.toLowerCase()).not.toContain('mandate');
    expect(element.querySelector('.stopped')).toBeNull();

    const revoke = vi.spyOn(TestBed.inject(RuleSetupService), 'revoke').mockReturnValue(undefined);
    (card.querySelector('.revoke') as HTMLButtonElement).click();
    expect(revoke).toHaveBeenCalledWith(expect.objectContaining({ id: 'r1' }));
  });

  it('lists stopped rules collapsed with their stop date and without Revoke', async () => {
    const rule: RuleView = {
      id: 'r1',
      template: 'SAFETY_CUSHION',
      summary: 'Keep my current account above 0 EUR',
      limits: { maxPerAction: '300.00', maxPerMonth: '600.00', floor: '2000.00' },
      activeSince: '2026-10-03',
      actionCount: 1,
      stoppedOn: '2026-10-15',
    };
    const element = await render(home(), null, [rule]);
    expect(element.querySelector('.rule')).toBeNull();
    expect(text(element.querySelector('section:has(.stopped) .empty'))).toBe('You have no active rules.');
    const stopped = element.querySelector('details.stopped') as HTMLDetailsElement;
    expect(stopped.open).toBe(false);
    expect(text(stopped.querySelector('summary'))).toBe('Stopped rules (1)');
    expect(text(stopped.querySelector('.stopped-summary'))).toBe('Keep my current account above 0 EUR');
    expect(text(stopped.querySelector('li .supporting'))).toBe('Stopped on 15 Oct 2026');
    expect(stopped.querySelector('.revoke')).toBeNull();
  });

  it("lists Jonas's contracts with the duplicate and missing-cover lines", async () => {
    const element = await render(home());
    const flags = Array.from(element.querySelectorAll('.flag'));
    expect(flags.map((f) => text(f.querySelector('.flag-text')))).toEqual([
      'You may be paying twice for liability insurance',
      "You don't have occupational disability insurance.",
    ]);
    expect(text(flags[0].querySelector('.supporting'))).toBe(
      'Policies held by Jonas and Parents of Jonas',
    );
    expect(flags.every((f) => f.querySelector('app-why-link button'))).toBe(true);

    const rows = Array.from(element.querySelectorAll('.contract'));
    expect(rows).toHaveLength(2);
    expect(text(rows[0].querySelector('.contract-type'))).toBe('Private liability insurance');
    expect(text(rows[0].querySelector('.premium'))).toBe('48.00 EUR');
    expect(text(rows[0].querySelector('.interval'))).toBe('yearly');
    expect(text(rows[0].querySelector('.due'))).toBe('Next due 1 Feb 2027');
    expect(rows[0].querySelector('app-why-link button')).not.toBeNull();
    expect(text(rows[1].querySelector('.paid-by'))).toBe('Paid by Parents of Jonas');
    expect(rows[1].querySelector('app-why-link')).toBeNull();

    // No insurer, recommendation or tariff comparison.
    const insurance = text(element.querySelector('.insurance'));
    expect(insurance).not.toMatch(/recommend|tariff|cheaper|switch|Example/i);
  });

  it('names a policyholder of two contracts only once', async () => {
    const base = home();
    const own = base.insurance.contracts[0];
    const element = await render(
      home({
        insurance: {
          contracts: [own, { ...own, id: 'second' }],
          flags: [{ ...base.insurance.flags[0], contractIds: [own.id, 'second'] }],
        },
      }),
    );
    expect(text(element.querySelector('.flag .supporting'))).toBe('Policies held by Jonas');
  });

  it('opens Why? on a flag with its own key', async () => {
    const element = await render(home());
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue(undefined as never);
    (element.querySelector('.flag app-why-link button') as HTMLElement).click();
    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { provId: 'a00000000000000a' } }),
    );
  });

  it('lists nothing but both missing-cover lines without contracts', async () => {
    const element = await render(
      home({
        insurance: {
          contracts: [],
          flags: [
            {
              kind: 'MISSING_COVER',
              coverage: 'liability',
              provId: 'b00000000000000a',
              contractIds: [],
            },
            {
              kind: 'MISSING_COVER',
              coverage: 'occupational disability',
              provId: 'b00000000000000b',
              contractIds: [],
            },
          ],
        },
      }),
    );
    expect(element.querySelectorAll('.contract')).toHaveLength(0);
    expect(Array.from(element.querySelectorAll('.flag-text')).map(text)).toEqual([
      "You don't have liability insurance.",
      "You don't have occupational disability insurance.",
    ]);
  });

  it('says so when the overview cannot be loaded', async () => {
    const fixture = TestBed.createComponent(HomeTabComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/overview/home')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain("We couldn't load your overview.");
  });

  it('shows no statement card until the chat made one', async () => {
    const element = await render(home());
    expect(element.querySelector('.needs')).toBeNull();
  });

  it('shows the demands-and-needs statement in Your insurance with Why? on every figure', async () => {
    const needs: NeedsStatement = {
      areas: [
        {
          area: 'Liability',
          need: 'Everyone needs it.',
          have: 'Your own policy.',
          check: 'Check with Parents of Jonas.',
        },
        { area: 'Occupational disability', need: 'It replaces income.', have: 'None.', check: 'Check.' },
        { area: 'Cover held twice', need: 'Paying twice.', have: 'Liability twice.', check: 'Check.' },
      ],
      facts: [{ label: 'Household', value: 'I live alone' }],
      figures: [
        figure('n000000000000001', '2', 'COUNT'),
        figure('n000000000000002', '0', 'COUNT'),
        figure('n000000000000003', '1', 'COUNT'),
      ],
    };
    const element = await render(home(), needs);
    const card = element.querySelector('.insurance .needs');
    expect(text(card?.querySelector('h3'))).toBe('Your demands and needs');
    expect(Array.from(card?.querySelectorAll('.needs-area') ?? []).map(text)).toEqual([
      'Liability',
      'Occupational disability',
      'Cover held twice',
    ]);
    expect(text(card)).toContain('Check with Parents of Jonas.');
    expect(card?.querySelectorAll('.needs-figures app-why-link')).toHaveLength(3);
  });
});
