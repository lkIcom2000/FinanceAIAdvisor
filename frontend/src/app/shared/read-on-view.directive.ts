import { DestroyRef, Directive, ElementRef, effect, inject, input, output } from '@angular/core';

/** How long an unread item has to stay in view to count as read (UX-DR39). */
export const READ_AFTER_MS = 5_000;

/** The share of the item that has to be in view. */
export const READ_VISIBLE_SHARE = 0.5;

/**
 * Emits `seen` once the host has stayed at least half in view for 5 s while `appReadOnView` is true. Without
 * IntersectionObserver (old browsers, tests without a stub) nothing is emitted.
 */
@Directive({ selector: '[appReadOnView]' })
export class ReadOnViewDirective {
  readonly appReadOnView = input.required<boolean>();
  readonly seen = output<void>();

  private timer: ReturnType<typeof setTimeout> | undefined;
  private observer: IntersectionObserver | undefined;

  constructor() {
    const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    inject(DestroyRef).onDestroy(() => this.disconnect());
    effect(() => {
      this.disconnect();
      if (!this.appReadOnView() || typeof IntersectionObserver === 'undefined') {
        return;
      }
      this.observer = new IntersectionObserver(
        (entries) => {
          const entry = entries[entries.length - 1];
          clearTimeout(this.timer);
          if (entry.isIntersecting && entry.intersectionRatio >= READ_VISIBLE_SHARE) {
            this.timer = setTimeout(() => {
              this.disconnect();
              this.seen.emit();
            }, READ_AFTER_MS);
          }
        },
        { threshold: READ_VISIBLE_SHARE },
      );
      this.observer.observe(element);
    });
  }

  private disconnect(): void {
    clearTimeout(this.timer);
    this.observer?.disconnect();
    this.observer = undefined;
  }
}
