import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ChatMessage } from '../../api/models/chat-message';
import { httpTestingProviders } from '../../testing/session-testing';
import {
  ChatColumnComponent,
  ICON_PATHS,
  OUTCOME_LABELS,
  Outcome,
  SLOW_AFTER_MS,
  STEP_INTERVAL_MS,
} from './chat-column.component';

const GREETING =
  "Hi Lena, I'm Fine. I can explain money topics, look at your situation and calculate what-ifs. " +
  "Every number I show you comes with a 'Why?'.";
const FAILED =
  "Something went wrong on our side, so I couldn't finish the answer. Your question is saved.";
const SLOW =
  'This is taking longer than usual. Fine runs on a local model and checks every answer.';

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

const NO_VALID_SOURCE =
  "I can't answer this reliably: there is no valid source on this topic in my knowledge base for " +
  "today's date, and I don't answer such questions from memory. A bank advisor can help you with it, " +
  'or you can ask me about a related topic I have sources for.';

const question: ChatMessage = {
  id: 'q1',
  author: 'USER',
  text: 'What is an ETF?',
  suggestions: [],
  checks: [],
  figures: [],
};
const answer: ChatMessage = {
  id: 'a1',
  author: 'AGENT',
  text: 'An ETF is a fund traded on an exchange.',
  outcome: 'INFORM',
  replyTo: 'q1',
  suggestions: [],
  checks: [],
  figures: [],
};

describe('ChatColumnComponent', () => {
  let fixture: ComponentFixture<ChatColumnComponent>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [ChatColumnComponent],
      providers: [
        ...httpTestingProviders(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    document.querySelectorAll('.cdk-overlay-container').forEach((e) => (e.innerHTML = ''));
    http.verify();
    vi.useRealTimers();
  });

  function render(messages: ChatMessage[] = []): void {
    fixture = TestBed.createComponent(ChatColumnComponent);
    fixture.detectChanges();
    http.expectOne('/api/chat').flush({ personaName: 'Lena', messages });
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function field(): HTMLTextAreaElement {
    return element.querySelector('textarea') as HTMLTextAreaElement;
  }

  function typeAndSend(value: string): void {
    field().value = value;
    field().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    field().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
    fixture.detectChanges();
  }

  function advance(ms: number): void {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  }

  function activeStep(): string {
    return text(element.querySelector('.waiting .step.active .step-label'));
  }

  it('greets an empty conversation with three suggestion chips that send their text', () => {
    render();
    expect(text(element.querySelector('.greeting .body'))).toBe(GREETING);
    const chips = Array.from(element.querySelectorAll<HTMLButtonElement>('.greeting .chip'));
    expect(chips.map(text)).toEqual([
      'How does an emergency fund work?',
      'What is an ETF savings plan?',
      'How is my liquidity looking?',
    ]);

    chips[1].click();
    fixture.detectChanges();
    const post = http.expectOne('/api/chat/messages');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ text: 'What is an ETF savings plan?' });
    expect(element.querySelector('.greeting')).toBeNull();
  });

  it('lists the history as a polite log, without the greeting', () => {
    render([question, answer]);
    const log = element.querySelector('.log');
    expect(log?.getAttribute('role')).toBe('log');
    expect(log?.getAttribute('aria-live')).toBe('polite');
    expect(element.querySelector('.greeting')).toBeNull();
    expect(text(element.querySelector('.user-message'))).toBe('What is an ETF?');
    expect(text(element.querySelector('.response .outcome-label'))).toBe('Explanation');
    expect(text(element.querySelector('.response .body'))).toBe(answer.text);
    expect(element.querySelector('.failed')).toBeNull();
  });

  it('starts every response card with the icon and plain label of its outcome', () => {
    const outcomes = Object.keys(OUTCOME_LABELS) as Outcome[];
    expect(outcomes).toHaveLength(7);
    const messages: ChatMessage[] = outcomes.flatMap((outcome, i) => [
      { id: `q${i}`, author: 'USER', text: `question ${i}`, suggestions: [], checks: [], figures: [] },
      {
        id: `a${i}`,
        author: 'AGENT',
        text: `answer ${i}`,
        outcome,
        replyTo: `q${i}`,
        suggestions: [],
        checks: [],
        figures: [],
      },
    ]);
    render(messages);

    const cards = Array.from(element.querySelectorAll('.response'));
    expect(cards).toHaveLength(7);
    expect(cards.map((card) => text(card.querySelector('.outcome-label')))).toEqual([
      'Explanation',
      'Calculated for you',
      'Recommendation',
      'A few questions first',
      'Not part of this service',
      "Let's check the question",
      'Passed to a human advisor',
    ]);
    cards.forEach((card, i) => {
      // First in reading order: the label row comes before the body; the icon is decorative.
      expect(card.firstElementChild?.classList).toContain('outcome-label');
      const icon = card.querySelector('.outcome-label svg');
      expect(icon?.getAttribute('aria-hidden')).toBe('true');
      expect(icon?.querySelector('path')?.getAttribute('d')).toBe(
        ICON_PATHS[OUTCOME_LABELS[outcomes[i]].icon],
      );
      expect(ICON_PATHS[OUTCOME_LABELS[outcomes[i]].icon]).toBeTruthy();
      expect(text(card.querySelector('.body'))).toBe(`answer ${i}`);
    });
  });

  it('shows the no-valid-source answer with one chip that sends its question', () => {
    render([
      question,
      {
        ...answer,
        text: NO_VALID_SOURCE,
        suggestions: ['What is an ETF savings plan?'],
      },
    ]);
    const card = element.querySelector('.response') as HTMLElement;
    expect(text(card.querySelector('.outcome-label'))).toBe('Explanation');
    expect(text(card.querySelector('.body'))).toBe(NO_VALID_SOURCE);
    const chips = Array.from(card.querySelectorAll<HTMLButtonElement>('.chip'));
    expect(chips.map(text)).toEqual(['What is an ETF savings plan?']);

    chips[0].click();
    fixture.detectChanges();
    const post = http.expectOne('/api/chat/messages');
    expect(post.request.body).toEqual({ text: 'What is an ETF savings plan?' });
    expect(element.querySelector('.waiting')).not.toBeNull();
  });

  it('disables the suggestion chips while a turn is pending, like the composer', () => {
    render([question, { ...answer, suggestions: ['What is an ETF savings plan?'] }]);
    const chip = () => element.querySelector('.response .chip') as HTMLButtonElement;
    expect(chip().disabled).toBe(false);

    typeAndSend('How does an emergency fund work?');
    expect(field().disabled).toBe(true);
    expect(chip().disabled).toBe(true);
    chip().click();
    fixture.detectChanges();
    http.expectOne('/api/chat/messages').flush(
      {
        id: 'q2',
        author: 'USER',
        text: 'How does an emergency fund work?',
        suggestions: [],
        checks: [],
        figures: [],
      },
      { status: 201, statusText: 'Created' },
    );
    fixture.detectChanges();
    expect(chip().disabled).toBe(true);

    http.expectOne('/api/chat/messages/q2/answer').flush({ ...answer, id: 'a2', replyTo: 'q2' });
    fixture.detectChanges();
    expect(chip().disabled).toBe(false);
    expect(field().disabled).toBe(false);
  });

  it('shows the check footer with the passed checks and opens "Why?" on the Checks section', () => {
    render([
      question,
      {
        ...answer,
        checks: [
          {
            id: 'SCOPE',
            passed: true,
            description: 'The answer stays within what this service covers',
          },
          {
            id: 'NO_PRODUCT_NAMING',
            passed: true,
            description: 'No single product, provider or ISIN is pushed',
          },
          {
            id: 'SOURCE_VALIDITY',
            passed: false,
            description: 'The explanation is based on valid, cited sources',
          },
        ],
      },
    ]);
    const footer = element.querySelector('.response .check-footer') as HTMLButtonElement;
    expect(text(footer)).toBe('Checked · 2 of 3 checks passed');

    footer.click();
    fixture.detectChanges();
    const dialog = document.querySelector('app-why-dialog') as HTMLElement;
    expect(text(dialog.querySelector('h2'))).toBe('How this answer was checked');
    expect(Array.from(dialog.querySelectorAll('h3')).map(text)).toEqual(['Checks']);
    const items = Array.from(dialog.querySelectorAll('.checks li'));
    expect(items.map((li) => text(li.querySelector('.mark')))).toEqual(['✔', '✔', '✘']);
    expect(text(items[2])).toContain(
      'Not passed: The explanation is based on valid, cited sources',
    );
    expect(items.map((li) => text(li.querySelector('small')))).toEqual([
      'SCOPE',
      'NO_PRODUCT_NAMING',
      'SOURCE_VALIDITY',
    ]);
  });

  it('words the footer for one, several and all passed checks', () => {
    const scope = {
      id: 'SCOPE' as const,
      passed: true,
      description: 'The answer stays within what this service covers',
    };
    const naming = {
      id: 'NO_PRODUCT_NAMING' as const,
      passed: true,
      description: 'No single product, provider or ISIN is pushed',
    };
    render([
      question,
      { ...answer, checks: [scope] },
      { ...question, id: 'q2' },
      { ...answer, id: 'a2', replyTo: 'q2', checks: [scope, naming] },
    ]);
    expect(Array.from(element.querySelectorAll('.check-footer')).map(text)).toEqual([
      'Checked · 1 check passed',
      'Checked · 2 checks passed',
    ]);
  });

  it('shows no check footer on an answer without final checks', () => {
    render([question, { ...answer, text: NO_VALID_SOURCE }]);
    expect(element.querySelector('.check-footer')).toBeNull();
  });

  it('shows a handover with its plain reason and status, and keeps the composer usable', () => {
    const handover =
      "Your question has been passed to a human advisor at your bank. You'll see their answer here in the chat.";
    render([
      question,
      {
        ...answer,
        text: handover,
        outcome: 'ESCALATE',
        handover: { reason: "because I couldn't confirm every figure in my answer", status: 'OPEN' },
      },
    ]);
    expect(text(element.querySelector('.response .outcome-label'))).toBe('Passed to a human advisor');
    expect(text(element.querySelector('.response .body'))).toBe(handover);
    expect(text(element.querySelector('.handover-reason'))).toBe(
      "It was passed on because I couldn't confirm every figure in my answer.",
    );
    expect(text(element.querySelector('.handover-status'))).toBe('Waiting for advisor');
    expect(field().disabled).toBe(false);
  });

  it('shows no reason line for a handover without a reason class, and no handover on other cards', () => {
    render([
      question,
      { ...answer, outcome: 'ESCALATE', handover: { status: 'OPEN' } },
      { ...question, id: 'q2' },
      { ...answer, id: 'a2', replyTo: 'q2' },
    ]);
    expect(element.querySelector('.handover-reason')).toBeNull();
    expect(element.querySelectorAll('.handover-status').length).toBe(1);
  });

  it('shows no chips on a card without suggestions', () => {
    render([question, answer]);
    expect(element.querySelector('.response .chips')).toBeNull();
  });

  it('runs a turn: saves the question, waits with timed steps, then shows the response card', () => {
    render();
    typeAndSend('What is an ETF?');

    // The waiting card appears at once; the composer is disabled.
    expect(element.querySelector('.waiting')).not.toBeNull();
    expect(text(element.querySelector('.waiting .sr-only'))).toBe('Fine is checking the answer');
    expect(activeStep()).toBe('Understanding your question');
    expect(field().disabled).toBe(true);
    expect(field().placeholder).toBe('Fine is checking the answer…');

    http.expectOne('/api/chat/messages').flush(question, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(text(element.querySelector('.user-message'))).toBe('What is an ETF?');
    const answerCall = http.expectOne('/api/chat/messages/q1/answer');
    expect(answerCall.request.method).toBe('POST');

    advance(STEP_INTERVAL_MS);
    expect(activeStep()).toBe('Getting your figures');
    advance(STEP_INTERVAL_MS * 2);
    expect(activeStep()).toBe('Checking the rules');
    // No ticks: steps are never shown as confirmed.
    expect(element.querySelector('.waiting')?.textContent).not.toMatch(/[✓✔]/);
    expect(element.querySelector('.waiting svg')).toBeNull();
    expect(element.querySelector('.slow')).toBeNull();

    advance(SLOW_AFTER_MS - STEP_INTERVAL_MS * 3);
    expect(text(element.querySelector('.slow'))).toBe(SLOW);

    // It never times out on its own.
    advance(10 * 60 * 1000);
    expect(element.querySelector('.waiting')).not.toBeNull();
    expect(activeStep()).toBe('Checking the rules');

    answerCall.flush(answer);
    fixture.detectChanges();
    expect(element.querySelector('.waiting')).toBeNull();
    expect(text(element.querySelector('.response .outcome-label'))).toBe('Explanation');
    expect(text(element.querySelector('.response .body'))).toBe(answer.text);
    expect(element.querySelector('.response')?.textContent).not.toMatch(/Checked/);
    expect(field().disabled).toBe(false);
  });

  it('shows the failed card when the answer call fails; Try again repeats only the answer call', () => {
    render();
    typeAndSend('What is an ETF?');
    http.expectOne('/api/chat/messages').flush(question, { status: 201, statusText: 'Created' });
    http
      .expectOne('/api/chat/messages/q1/answer')
      .flush(
        { code: 'TURN_FAILED', detail: FAILED },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();

    expect(element.querySelector('.waiting')).toBeNull();
    expect(text(element.querySelector('.failed .body'))).toBe(FAILED);
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(field().disabled).toBe(false);

    (element.querySelector('.failed .try-again') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('.failed')).toBeNull();
    expect(element.querySelector('.waiting')).not.toBeNull();
    http.expectNone('/api/chat/messages');
    http.expectOne('/api/chat/messages/q1/answer').flush(answer);
    fixture.detectChanges();
    expect(element.querySelectorAll('.user-message')).toHaveLength(1);
    expect(text(element.querySelector('.response .body'))).toBe(answer.text);
  });

  it('offers Try again for a saved question that has no answer yet', () => {
    render([question]);
    expect(text(element.querySelector('.failed .body'))).toBe(FAILED);
    (element.querySelector('.failed .try-again') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne('/api/chat/messages/q1/answer').flush(answer);
    fixture.detectChanges();
    expect(element.querySelector('.failed')).toBeNull();
    expect(element.querySelector('.response')).not.toBeNull();
  });

  it('puts the text back when the question cannot be saved', () => {
    render();
    typeAndSend('What is an ETF?');
    http
      .expectOne('/api/chat/messages')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(element.querySelector('.waiting')).toBeNull();
    expect(field().value).toBe('What is an ETF?');
    expect(text(element.querySelector('.send-failed'))).toBe(
      "Your message couldn't be sent. Please try again.",
    );
  });

  it('keeps the scroll position and shows a New message pill when scrolled up', () => {
    render();
    const log = element.querySelector('.log') as HTMLElement;
    Object.defineProperty(log, 'scrollHeight', { configurable: true, value: 1000 });
    Object.defineProperty(log, 'clientHeight', { configurable: true, value: 300 });

    typeAndSend('What is an ETF?');
    http.expectOne('/api/chat/messages').flush(question, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    log.scrollTop = 100;
    log.dispatchEvent(new Event('scroll'));

    http.expectOne('/api/chat/messages/q1/answer').flush(answer);
    fixture.detectChanges();
    expect(log.scrollTop).toBe(100);
    const pill = element.querySelector('.new-message') as HTMLButtonElement;
    expect(text(pill)).toBe('New message');

    pill.click();
    fixture.detectChanges();
    expect(element.querySelector('.new-message')).toBeNull();
  });

  it('shows no pill when the reader is at the bottom', () => {
    render();
    typeAndSend('What is an ETF?');
    http.expectOne('/api/chat/messages').flush(question, { status: 201, statusText: 'Created' });
    http.expectOne('/api/chat/messages/q1/answer').flush(answer);
    fixture.detectChanges();
    expect(element.querySelector('.new-message')).toBeNull();
  });

  const item40: ChatMessage = {
    id: 'q40',
    author: 'USER',
    text: 'I have a portfolio of 500,000 EUR ...',
    suggestions: [],
    checks: [],
    figures: [],
  };
  const proposal: ChatMessage = {
    id: 'p40',
    author: 'AGENT',
    text: "Here's what I understood from your question.",
    outcome: 'COMPUTE',
    replyTo: 'q40',
    suggestions: [],
    checks: [],
    figures: [],
    proposal: {
      status: 'OPEN',
      parameters: [
        { key: 'portfolio', label: 'Starting portfolio', value: '48210.00', unit: 'EUR', source: 'ACCOUNT_DATA', provId: 'wea0000000000a3' },
        { key: 'monthlyWithdrawal', label: 'Monthly withdrawal', value: '2000.00', unit: 'EUR', source: 'QUESTION' },
        { key: 'annualRate', label: 'Assumed annual return', value: '0.04', unit: 'FRACTION', source: 'QUESTION' },
      ],
    },
  };
  const calculated: ChatMessage = {
    id: 'c40',
    author: 'AGENT',
    text: 'It lasts 522 full monthly withdrawals.',
    outcome: 'COMPUTE',
    replyTo: 'p40',
    suggestions: [],
    checks: [],
    figures: [
      { provId: 'calc000000000a1', label: 'Full monthly withdrawals', value: '522', unit: 'COUNT' },
      { provId: 'wea0000000000a3', label: 'Value of your investments today', value: '48210.00', unit: 'EUR' },
    ],
  };

  function flushOverviewIndex(): void {
    http.expectOne('/api/overview/home').flush({});
    http.expectOne('/api/overview/liquidity').flush({});
    http.expectOne('/api/overview/wealth').flush({ projection: { initial: { provId: 'wea0000000000a3' } } });
    fixture.detectChanges();
  }

  it('shows a proposal with each value, its unit and its source, and nothing calculated', () => {
    render([item40, proposal]);

    expect(text(element.querySelector('.proposal-title'))).toBe("Here's what I understood");
    const rows = Array.from(element.querySelectorAll('.proposal-row')).map(text);
    expect(rows).toEqual([
      'Starting portfolio 48,210.00 EUR From your account data',
      'Monthly withdrawal 2,000.00 EUR From your question',
      'Assumed annual return 4 % From your question',
    ]);
    expect(element.querySelector('.figures')).toBeNull();
    expect(element.querySelector('.check-footer')).toBeNull();
  });

  it('calculates on Calculate and shows the answer with figure chips linked to the Overview', () => {
    render([item40, proposal]);
    element.querySelector<HTMLButtonElement>('.calculate')!.click();
    fixture.detectChanges();

    const call = http.expectOne('/api/chat/messages/p40/calculate');
    expect(call.request.body).toEqual({ parameters: [] });
    expect(element.querySelector('.waiting')).not.toBeNull();
    call.flush(calculated);
    fixture.detectChanges();
    flushOverviewIndex();

    expect(element.querySelector('.proposal-actions')).toBeNull();
    const chips = Array.from(element.querySelectorAll('.figure-chip')).map(text);
    expect(chips).toEqual(['Full monthly withdrawals 522', 'Value of your investments today 48,210.00 EUR']);
    const links = element.querySelectorAll('.show-in-overview');
    expect(links.length).toBe(1);
    expect(links[0].closest('.figure')?.textContent).toContain('48,210.00 EUR');

    element.querySelector<HTMLButtonElement>('.figure-chip')!.click();
    http.expectOne('/api/provenance/calc000000000a1');
  });

  it('turns the rows into fields on Change values and sends only the changed values, a rate as a share', () => {
    render([item40, proposal]);
    element.querySelector<HTMLButtonElement>('.change-values')!.click();
    fixture.detectChanges();

    const fields = Array.from(element.querySelectorAll<HTMLInputElement>('.proposal input'));
    expect(fields.map((f) => f.value)).toEqual(['48210.00', '2000.00', '4']);
    fields[2].value = '3';
    fields[2].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.calculate')!.click();

    expect(http.expectOne('/api/chat/messages/p40/calculate').request.body).toEqual({
      parameters: [{ key: 'annualRate', value: '0.03' }],
    });
  });

  it('refuses a field that is not a number before calling the backend', () => {
    render([item40, proposal]);
    element.querySelector<HTMLButtonElement>('.change-values')!.click();
    fixture.detectChanges();
    const field = element.querySelectorAll<HTMLInputElement>('.proposal input')[1];
    field.value = 'two thousand';
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('.calculate')!.click();
    fixture.detectChanges();

    expect(text(element.querySelector('.proposal-error'))).toBe(
      'Please enter a number for Monthly withdrawal.',
    );
  });

  it('greys an open proposal as Cancelled when a new question is sent', () => {
    render([item40, proposal]);
    typeAndSend('What is an ETF?');
    expect(element.querySelector('.calculate')).not.toBeNull();
    http.expectOne('/api/chat/messages').flush({
      id: 'q2',
      author: 'USER',
      text: 'What is an ETF?',
      suggestions: [],
      checks: [],
      figures: [],
    });
    fixture.detectChanges();

    expect(element.querySelector('.proposal')?.classList).toContain('cancelled');
    expect(text(element.querySelector('.proposal-status'))).toBe('Cancelled');
    expect(element.querySelector('.calculate')).toBeNull();
    http.expectOne('/api/chat/messages/q2/answer');
  });

  it('keeps a proposal open when the new question cannot be sent', () => {
    render([item40, proposal]);
    typeAndSend('What is an ETF?');
    http
      .expectOne('/api/chat/messages')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element.querySelector('.proposal')?.classList).not.toContain('cancelled');
    expect(element.querySelector('.calculate')).not.toBeNull();
  });

  it('shows a proposal the backend already cancelled as Cancelled after Calculate', () => {
    render([item40, proposal]);
    element.querySelector<HTMLButtonElement>('.calculate')!.click();
    http
      .expectOne('/api/chat/messages/p40/calculate')
      .flush({ code: 'PROPOSAL_CLOSED' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text(element.querySelector('.proposal-status'))).toBe('Cancelled');
  });

  it('indexes the Overview on load when the history has figure chips', () => {
    render([item40, { ...proposal, proposal: { ...proposal.proposal!, status: 'CALCULATED' } }, calculated]);
    flushOverviewIndex();

    expect(element.querySelectorAll('.show-in-overview').length).toBe(1);
  });
});
