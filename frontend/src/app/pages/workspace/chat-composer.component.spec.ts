import { TestBed } from '@angular/core/testing';
import { ChatComposerComponent } from './chat-composer.component';

describe('ChatComposerComponent', () => {
  let sent: string[];

  function render(inputs: { busy?: boolean } = {}) {
    const fixture = TestBed.createComponent(ChatComposerComponent);
    if (inputs.busy !== undefined) {
      fixture.componentRef.setInput('busy', inputs.busy);
    }
    sent = [];
    fixture.componentInstance.send.subscribe((text) => sent.push(text));
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const field = element.querySelector('textarea') as HTMLTextAreaElement;
    const type = (value: string) => {
      field.value = value;
      field.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const press = (init: KeyboardEventInit) => {
      const event = new KeyboardEvent('keydown', { cancelable: true, ...init });
      field.dispatchEvent(event);
      fixture.detectChanges();
      return event;
    };
    return { fixture, element, field, type, press };
  }

  it('sends on Enter and clears the field', () => {
    const { field, type, press } = render();
    type('What is an ETF?');
    const event = press({ key: 'Enter' });
    expect(event.defaultPrevented).toBe(true);
    expect(sent).toEqual(['What is an ETF?']);
    expect(field.value).toBe('');
  });

  it('adds a new line on Shift+Enter instead of sending', () => {
    const { type, press } = render();
    type('Line one');
    const event = press({ key: 'Enter', shiftKey: true });
    expect(event.defaultPrevented).toBe(false);
    expect(sent).toEqual([]);
  });

  it('does not send blank text', () => {
    const { type, press } = render();
    type('   ');
    press({ key: 'Enter' });
    expect(sent).toEqual([]);
  });

  it('stops at 1,000 characters and shows the counter from 800', () => {
    const { element, field, type } = render();
    expect(field.getAttribute('maxlength')).toBe('1000');

    type('x'.repeat(799));
    expect(element.querySelector('.counter')).toBeNull();
    type('x'.repeat(800));
    expect(element.querySelector('.counter')?.textContent?.trim()).toBe('800 / 1,000');

    type('x'.repeat(1005));
    expect(field.value.length).toBe(1000);
    expect(element.querySelector('.counter')?.textContent?.trim()).toBe('1,000 / 1,000');
  });

  it('is disabled with the checking placeholder while a turn is in progress', () => {
    const { field, press } = render({ busy: true });
    expect(field.disabled).toBe(true);
    expect(field.placeholder).toBe('Fine is checking the answer…');
    press({ key: 'Enter' });
    expect(sent).toEqual([]);
  });
});
