import { HttpTestingController } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ProvenanceResponse } from '../api/models/provenance-response';
import { flushMicrotasks, httpTestingProviders } from '../testing/session-testing';
import { WhyLinkComponent } from './why-link.component';

@Component({
  imports: [WhyLinkComponent],
  template: `<app-why-link provId="aaaaaaaaaaaaaaaa" />`,
})
class HostComponent {}

const BALANCE: ProvenanceResponse = {
  provId: 'aaaaaaaaaaaaaaaa',
  label: 'Current account balance',
  value: '1974.00',
  unit: 'EUR',
  asOf: '2026-10-15',
  source: {
    kind: 'CORE_FUNCTION',
    description:
      "Calculated by Fine's calculation engine (current account balance), not by the language model. " +
      'Based on your account data from 1 Oct 2025 to 15 Oct 2026.',
  },
  inputs: [
    { label: 'Balance when the account was opened on 1 Oct 2025', value: '1000.00', unit: 'EUR' },
    { label: 'Average net income', value: '2100.00', unit: 'EUR', provId: 'bbbbbbbbbbbbbbbb' },
    { label: 'Months used', text: 'the last 12 months' },
  ],
  assumptions: ['Includes every booking up to the end of 15 Oct 2026.'],
  ruleParameters: [],
  checks: [],
};

const INCOME: ProvenanceResponse = {
  ...BALANCE,
  provId: 'bbbbbbbbbbbbbbbb',
  label: 'Average net income',
  value: '-2100.00',
  inputs: [],
  checks: [
    { id: 'NUMBER_MATCH', passed: true, description: 'Every number matches the calculation' },
  ],
};

describe('"Why?" dialog', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        ...httpTestingProviders(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function openDialog(first: ProvenanceResponse | null = BALANCE) {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector(
      'button',
    ) as HTMLButtonElement;
    expect(trigger.textContent).toBe('Why?');
    trigger.focus();
    trigger.click();
    fixture.detectChanges();
    const request = http.expectOne('/api/provenance/aaaaaaaaaaaaaaaa');
    if (first) {
      request.flush(first);
    } else {
      request.flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    }
    await settle(fixture);
    return {
      fixture,
      trigger,
      dialog: () => document.querySelector('app-why-dialog') as HTMLElement,
    };
  }

  async function settle(fixture: {
    detectChanges(): void;
    whenStable(): Promise<unknown>;
  }): Promise<void> {
    await flushMicrotasks();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    document.querySelectorAll('.cdk-overlay-container').forEach((e) => (e.innerHTML = ''));
  });

  it('shows the four sections in order with the provenance values', async () => {
    const { dialog } = await openDialog();
    const element = dialog();

    expect(element.querySelector('h2')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Current account balance: 1,974.00 EUR',
    );
    expect(Array.from(element.querySelectorAll('h3')).map((h) => h.textContent)).toEqual([
      'Where this number comes from',
      'What went in',
      'Assumptions',
      'Checks',
    ]);
    const text = element.textContent ?? '';
    expect(text).toContain("Calculated by Fine's calculation engine (current account balance)");
    expect(text).toContain('1,000.00 EUR');
    expect(text).toContain('the last 12 months');
    expect(text).toContain('Includes every booking up to the end of 15 Oct 2026.');
    expect(text).toContain('No checks apply to this figure.');
    expect(text).not.toMatch(/provId|aaaaaaaaaaaaaaaa|accountBalance|Check Chain/);
    expect(element.querySelector('.back')).toBeNull();
    expect(element.querySelector('.close')?.textContent?.trim()).toBe('Close');
  });

  it("drills down in place on an input's Why? and Back restores it", async () => {
    const { fixture, dialog } = await openDialog();
    const why = dialog().querySelectorAll('.inputs .why');
    expect(why.length).toBe(1);
    (why[0] as HTMLElement).click();
    http.expectOne('/api/provenance/bbbbbbbbbbbbbbbb').flush(INCOME);
    await settle(fixture);

    expect(document.querySelectorAll('app-why-dialog').length).toBe(1);
    expect(dialog().querySelector('h2')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Average net income: −2,100.00 EUR',
    );
    expect(dialog().textContent).toContain('Every number matches the calculation');
    expect(dialog().querySelector('.checks small')?.textContent).toBe('NUMBER_MATCH');

    (dialog().querySelector('.back') as HTMLElement).click();
    await settle(fixture);

    expect(dialog().querySelector('h2')?.textContent).toContain('Current account balance');
    expect(dialog().querySelector('.back')).toBeNull();
  });

  it('Close returns focus to the Why? trigger', async () => {
    const { fixture, trigger, dialog } = await openDialog();
    expect(document.activeElement).not.toBe(trigger);

    (dialog().querySelector('.close') as HTMLElement).click();
    await settle(fixture);
    await new Promise((resolve) => setTimeout(resolve, 50));
    await settle(fixture);

    expect(document.querySelector('app-why-dialog')).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it('Esc closes and returns focus to the trigger', async () => {
    const { fixture, trigger, dialog } = await openDialog();

    dialog().dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }),
    );
    await settle(fixture);
    await new Promise((resolve) => setTimeout(resolve, 50));
    await settle(fixture);

    expect(document.querySelector('app-why-dialog')).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it('replaces the content with an alert when the figure cannot be loaded', async () => {
    const { dialog } = await openDialog(null);

    expect(dialog().querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't load this explanation.",
    );
    expect(dialog().querySelectorAll('h3').length).toBe(0);
    expect(dialog().querySelector('.close')).not.toBeNull();
  });

  it('keeps the current figure when a drill-down fails and shows the error inline', async () => {
    const { fixture, dialog } = await openDialog();
    (dialog().querySelector('.inputs .why') as HTMLElement).click();
    await settle(fixture);
    expect(dialog().querySelector('[role="status"]')?.textContent).toContain('Loading…');

    http
      .expectOne('/api/provenance/bbbbbbbbbbbbbbbb')
      .flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle(fixture);

    expect(dialog().querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't load this explanation.",
    );
    expect(dialog().querySelector('h2')?.textContent).toContain('Current account balance');
    expect(dialog().querySelectorAll('h3').length).toBe(4);
    expect(dialog().querySelector('.inputs .why')).not.toBeNull();
    expect(dialog().querySelector('[role="status"]')).toBeNull();
  });

  it('ignores a second input click while a figure is loading', async () => {
    const { fixture, dialog } = await openDialog();
    const why = dialog().querySelector('.inputs .why') as HTMLElement;
    why.click();
    why.click();
    await settle(fixture);

    http.expectOne('/api/provenance/bbbbbbbbbbbbbbbb').flush(INCOME);
    await settle(fixture);
    http.verify();

    expect(dialog().querySelector('h2')?.textContent).toContain('Average net income');
    (dialog().querySelector('.back') as HTMLElement).click();
    await settle(fixture);
    expect(dialog().querySelector('h2')?.textContent).toContain('Current account balance');
    expect(dialog().querySelector('.back')).toBeNull();
  });

  it('lists rule parameters with version and validity under the source', async () => {
    const { dialog } = await openDialog({
      ...BALANCE,
      ruleParameters: [
        {
          id: 'emergency_fund_months_min',
          version: '2026-1',
          validFrom: '2026-01-01',
          validTo: '2026-12-31',
        },
        { id: 'projection_inflation', version: '3' },
      ],
    });

    const rules = Array.from(dialog().querySelectorAll('.rules li')).map((li) =>
      li.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(rules).toEqual([
      'Rule emergency_fund_months_min, version 2026-1, valid from 1 Jan 2026 to 31 Dec 2026',
      'Rule projection_inflation, version 3',
    ]);
  });
});
