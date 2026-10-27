import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { RuleView } from '../../api/models/rule-view';
import { flushMicrotasks, httpTestingProviders } from '../../testing/session-testing';
import { REVOKE_FAILED, RevokeRuleDialogComponent } from './revoke-rule-dialog.component';

const RULE: RuleView = {
  id: 'r1',
  template: 'SAFETY_CUSHION',
  summary:
    'Keep my current account above 0 EUR, using my overnight deposit, but never let the overnight deposit fall below 2,000 EUR',
  fromName: 'Overnight deposit',
  toName: 'Current account',
  limits: { maxPerAction: '300.00', maxPerMonth: '600.00', floor: '2000.00' },
  activeSince: '2026-10-03',
  actionCount: 1,
  undoUntil: 'Fri 16 Oct, 5 pm',
};

describe('RevokeRuleDialogComponent', () => {
  const close = vi.fn();
  const ref = { close, disableClose: false };
  const text = (e: Element | null) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

  async function render(rule: RuleView) {
    close.mockReset();
    await TestBed.configureTestingModule({
      imports: [RevokeRuleDialogComponent],
      providers: [
        ...httpTestingProviders(),
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: rule },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(RevokeRuleDialogComponent);
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('names the last undo deadline and offers Keep it and Stop this rule', async () => {
    const { element } = await render(RULE);
    expect(text(element.querySelector('h2'))).toBe('Stop this rule?');
    expect(text(element.querySelector('.consequence'))).toBe(
      "Fine won't move money under this rule anymore. Transfers already made stay as they are; you can still undo the last one until Fri 16 Oct, 5 pm.",
    );
    expect(text(element.querySelector('.keep'))).toBe('Keep it');
    expect(text(element.querySelector('.stop'))).toBe('Stop this rule');
  });

  it('leaves out the undo sentence when nothing can be undone', async () => {
    const { element } = await render({ ...RULE, undoUntil: undefined });
    expect(text(element.querySelector('.consequence'))).toBe(
      "Fine won't move money under this rule anymore. Transfers already made stay as they are.",
    );
  });

  it('stops the rule and closes with it', async () => {
    const { fixture, element } = await render(RULE);
    (element.querySelector('.stop') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((element.querySelector('.stop') as HTMLButtonElement).disabled).toBe(true);
    const stopped = { ...RULE, stoppedOn: '2026-10-15' };
    const req = TestBed.inject(HttpTestingController).expectOne('/api/rules/r1/revoke');
    expect(req.request.method).toBe('POST');
    req.flush(stopped);
    await flushMicrotasks();
    expect(close).toHaveBeenCalledWith({ stopped });
  });

  it('closes with the conflict detail, or a general text on other errors', async () => {
    const { element } = await render(RULE);
    const http = TestBed.inject(HttpTestingController);
    (element.querySelector('.stop') as HTMLButtonElement).click();
    http
      .expectOne('/api/rules/r1/revoke')
      .flush(
        { detail: 'This rule has already been stopped.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(close).toHaveBeenCalledWith({ failed: 'This rule has already been stopped.' });

    (element.querySelector('.stop') as HTMLButtonElement).click();
    http.expectOne('/api/rules/r1/revoke').flush(null, { status: 500, statusText: 'Error' });
    expect(close).toHaveBeenLastCalledWith({ failed: REVOKE_FAILED });
  });
});
