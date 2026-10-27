import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { TemplateView } from '../../api/models/template-view';
import { flushMicrotasks, httpTestingProviders } from '../../testing/session-testing';
import { NO_TEMPLATES, RuleSetupDialogComponent } from './rule-setup-dialog.component';

function figure(value: string) {
  return { provId: '0123456789abcdef', label: 'Suggested', value, unit: 'EUR' as const };
}

const CUSHION: TemplateView = {
  template: 'SAFETY_CUSHION',
  title: 'Keep a safety cushion in my current account',
  fromName: 'Overnight deposit',
  toName: 'Current account',
  alreadyActive: false,
  maxPerAction: figure('200.00'),
  maxPerMonth: figure('400.00'),
  floor: figure('2040.00'),
  source: 'Suggested by Fine from your lowest forecast balance in the next three months.',
};

const TOP_UP: TemplateView = {
  ...CUSHION,
  template: 'EMERGENCY_FUND_TOP_UP',
  title: 'Top up my emergency fund',
  fromName: 'Current account',
  toName: 'Overnight deposit',
  alreadyActive: true,
};

const SUMMARY =
  'Keep my current account above 0 EUR, using my overnight deposit, but never let the overnight deposit fall below 2,000 EUR';

describe('RuleSetupDialogComponent', () => {
  let fixture: ComponentFixture<RuleSetupDialogComponent>;
  let element: HTMLElement;
  let http: HttpTestingController;
  const close = vi.fn();
  const ref = { close, disableClose: false };

  const text = (e: Element | null) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const button = (selector: string) => element.querySelector(selector) as HTMLButtonElement;

  beforeEach(async () => {
    close.mockReset();
    await TestBed.configureTestingModule({
      imports: [RuleSetupDialogComponent],
      providers: [...httpTestingProviders(), { provide: MatDialogRef, useValue: ref }],
    }).compileComponents();
    fixture = TestBed.createComponent(RuleSetupDialogComponent);
    http = TestBed.inject(HttpTestingController);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle(): Promise<void> {
    await flushMicrotasks();
    fixture.detectChanges();
  }

  async function loaded(templates: TemplateView[] = [CUSHION, TOP_UP]): Promise<void> {
    http.expectOne('/api/rules/setup').flush(templates);
    await settle();
  }

  async function toLimits(): Promise<void> {
    await loaded();
    (element.querySelectorAll('.template')[0] as HTMLButtonElement).click();
    await settle();
  }

  it('offers both templates and marks one that is already active', async () => {
    await loaded();
    const templates = Array.from(element.querySelectorAll<HTMLButtonElement>('.template'));
    expect(templates.map((t) => text(t.querySelector('.template-title')))).toEqual([
      'Keep a safety cushion in my current account',
      'Top up my emergency fund',
    ]);
    expect(templates[0].getAttribute('aria-disabled')).toBe('false');
    expect(templates[1].getAttribute('aria-disabled')).toBe('true');
    templates[1].click();
    await settle();
    expect(element.querySelector('.field input')).toBeNull();
    expect(text(templates[1].querySelector('.template-active'))).toBe(
      'You already have this rule.',
    );
    expect(element.querySelector('[aria-current="step"]')?.textContent).toContain('Choose a rule');
  });

  it('pre-fills the limits the Core suggests and shows their source', async () => {
    await toLimits();
    const inputs = Array.from(element.querySelectorAll<HTMLInputElement>('.field input'));
    expect(inputs.map((i) => i.value)).toEqual(['200.00', '400.00', '2040.00']);
    expect(text(element.querySelector('.source'))).toBe(CUSHION.source);
    expect(element.querySelector('[aria-current="step"]')?.textContent).toContain('Set limits');
  });

  it('checks the limits on the server, shows the full summary and starts the rule', async () => {
    await toLimits();
    const floor = element.querySelectorAll<HTMLInputElement>('.field input')[2];
    floor.value = '2000';
    floor.dispatchEvent(new Event('input'));
    button('.next').click();
    const check = http.expectOne('/api/rules/check');
    expect(check.request.body).toEqual({
      template: 'SAFETY_CUSHION',
      maxPerAction: '200.00',
      maxPerMonth: '400.00',
      floor: '2000',
    });
    check.flush({
      summary: SUMMARY,
      will: ['Move at most 200 EUR per transfer and 400 EUR per month.'],
      wont: ['Take your overnight deposit below 2,000 EUR.'],
      undoAndStop: 'You can undo each transfer with one tap.',
    });
    await settle();
    expect(text(element.querySelector('.summary'))).toBe(SUMMARY);
    expect(text(element.querySelector('.wont'))).toBe(
      'Take your overnight deposit below 2,000 EUR.',
    );
    expect(text(button('.start'))).toBe('Start this rule');

    button('.start').click();
    const start = http.expectOne({ method: 'POST', url: '/api/rules' });
    const rule = { id: 'r1', summary: SUMMARY };
    start.flush(rule, { status: 201, statusText: 'Created' });
    expect(close).toHaveBeenCalledWith(rule);
  });

  it('shows the plain reason when the limits are refused', async () => {
    await toLimits();
    button('.next').click();
    http
      .expectOne('/api/rules/check')
      .flush(
        { detail: "The most per month can't be less than the most per transfer." },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    expect(text(element.querySelector('.problem'))).toBe(
      "The most per month can't be less than the most per transfer.",
    );
    expect(element.querySelector('.summary')).toBeNull();
  });

  it('explains when there is nothing to set up', async () => {
    await loaded([]);
    expect(text(element.querySelector('.problem'))).toBe(NO_TEMPLATES);
    expect(element.querySelector('.template')).toBeNull();
  });
});
