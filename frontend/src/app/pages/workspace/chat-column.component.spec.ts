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
};
const answer: ChatMessage = {
  id: 'a1',
  author: 'AGENT',
  text: 'An ETF is a fund traded on an exchange.',
  outcome: 'INFORM',
  replyTo: 'q1',
  suggestions: [],
  checks: [],
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
      { id: `q${i}`, author: 'USER', text: `question ${i}`, suggestions: [], checks: [] },
      {
        id: `a${i}`,
        author: 'AGENT',
        text: `answer ${i}`,
        outcome,
        replyTo: `q${i}`,
        suggestions: [],
        checks: [],
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
    http
      .expectOne('/api/chat/messages')
      .flush(
        {
          id: 'q2',
          author: 'USER',
          text: 'How does an emergency fund work?',
          suggestions: [],
          checks: [],
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
    expect(text(footer)).toBe('Checked · 2 checks passed');

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

  it('shows no check footer on an answer without final checks', () => {
    render([question, { ...answer, text: NO_VALID_SOURCE }]);
    expect(element.querySelector('.check-footer')).toBeNull();
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
});
