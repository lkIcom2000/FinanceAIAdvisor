import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { RuleView } from '../../api/models/rule-view';
import { RuleSetupService } from './rule-setup.service';

const RULE = { id: 'r1', summary: 'Keep my current account above 0 EUR' } as RuleView;

describe('RuleSetupService.revoke', () => {
  const open = vi.fn();
  const snack = vi.fn();

  beforeEach(() => {
    open.mockReset();
    snack.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialog, useValue: { open } },
        { provide: MatSnackBar, useValue: { open: snack } },
      ],
    });
  });

  function revokeClosingWith(result: unknown): RuleSetupService {
    open.mockReturnValue({ afterClosed: () => of(result) });
    const service = TestBed.inject(RuleSetupService);
    service.revoke(RULE);
    return service;
  }

  it('reloads the rules and confirms a stopped rule', () => {
    const service = revokeClosingWith({ stopped: { ...RULE, stoppedOn: '2026-10-15' } });
    expect(open.mock.calls[0][1].data).toBe(RULE);
    expect(service.changed()).toBe(1);
    expect(snack).toHaveBeenCalledWith('Your rule is stopped.', undefined, { duration: 5000 });
  });

  it('reloads the rules and shows why a rule could not be stopped', () => {
    const service = revokeClosingWith({ failed: 'This rule has already been stopped.' });
    expect(service.changed()).toBe(1);
    expect(snack).toHaveBeenCalledWith('This rule has already been stopped.', undefined, {
      duration: 5000,
    });
  });

  it('changes nothing on Keep it', () => {
    const service = revokeClosingWith(undefined);
    expect(service.changed()).toBe(0);
    expect(snack).not.toHaveBeenCalled();
  });
});
