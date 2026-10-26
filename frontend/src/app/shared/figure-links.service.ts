import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, forkJoin, map, of, catchError, tap } from 'rxjs';
import { OverviewService } from '../api/services/overview.service';
import type { OverviewTab } from '../pages/workspace/workspace.page';

/** How long a referenced Overview element keeps its highlight ring (UX-DR6). */
export const RING_MS = 4000;

/** The class that draws the cyan highlight ring (styles.scss). */
export const RING_CLASS = 'fine-highlight-ring';

/** Every `provId` in an Overview response, wherever it sits. */
export function provIdsIn(value: unknown, out: Set<string> = new Set()): Set<string> {
  if (Array.isArray(value)) {
    value.forEach((v) => provIdsIn(v, out));
  } else if (value && typeof value === 'object') {
    for (const [key, v] of Object.entries(value)) {
      if (key === 'provId' && typeof v === 'string') {
        out.add(v);
      } else {
        provIdsIn(v, out);
      }
    }
  }
  return out;
}

/**
 * Links chat figures to the Overview by figure key (FR-8, AD-19). It indexes the `provId`s of the three Overview
 * responses, because a tab that was never opened is not rendered; it rings matching elements on the shown tab for
 * 4 s, marks other tabs with a dot and asks the workspace to switch tabs only on "Show in overview".
 */
@Injectable({ providedIn: 'root' })
export class FigureLinksService {
  private readonly overview = inject(OverviewService);
  private readonly document = inject(DOCUMENT);

  /** `provId` → the tab that shows it. */
  readonly index = signal<ReadonlyMap<string, OverviewTab>>(new Map());
  /** Tabs whose label carries the cyan dot: they show a referenced figure while another tab is shown. */
  readonly dots = signal<ReadonlySet<OverviewTab>>(new Set());
  /** The tab on screen, kept by the workspace. */
  readonly activeTab = signal<OverviewTab>('home');
  /** A "Show in overview" the workspace has still to carry out. */
  readonly requested = signal<{ tab: OverviewTab; provId: string } | null>(null);

  private timers = new Map<Element, ReturnType<typeof setTimeout>>();

  /** Reloads the index from the Overview responses; a failed one leaves its tab out. */
  refresh(): Observable<ReadonlyMap<string, OverviewTab>> {
    const ids = (source: Observable<unknown>) =>
      source.pipe(
        map((value) => provIdsIn(value)),
        catchError(() => of(new Set<string>())),
      );
    return forkJoin({
      home: ids(this.overview.home()),
      liquidity: ids(this.overview.liquidity()),
      wealth: ids(this.overview.wealth()),
    }).pipe(
      map((byTab) => {
        const index = new Map<string, OverviewTab>();
        for (const tab of ['home', 'liquidity', 'wealth'] as const) {
          byTab[tab].forEach((id) => index.set(id, tab));
        }
        return index;
      }),
      tap((index) => this.index.set(index)),
    );
  }

  isInOverview(provId: string): boolean {
    return this.index().has(provId);
  }

  /** An answer referenced these figures: ring them on the shown tab, dot the other tabs that show any of them. */
  highlight(provIds: readonly string[]): void {
    const dots = new Set(this.dots());
    for (const id of provIds) {
      const tab = this.index().get(id);
      if (!tab) {
        continue;
      }
      if (tab === this.activeTab()) {
        this.elements(id).forEach((element) => this.ring(element, RING_MS));
      } else {
        dots.add(tab);
      }
    }
    this.dots.set(dots);
  }

  /** The workspace switched to `tab`: its dot goes. */
  shown(tab: OverviewTab): void {
    this.activeTab.set(tab);
    if (this.dots().has(tab)) {
      const dots = new Set(this.dots());
      dots.delete(tab);
      this.dots.set(dots);
    }
  }

  /** "Show in overview": the workspace switches to the figure's tab, then calls {@link reveal}. */
  show(provId: string): void {
    const tab = this.index().get(provId);
    if (tab) {
      this.requested.set({ tab, provId });
    }
  }

  /** Scrolls the requested figure into view and holds its ring until the next click anywhere. */
  reveal(): void {
    const request = this.requested();
    this.requested.set(null);
    const element = request ? this.elements(request.provId)[0] : undefined;
    if (!element) {
      return;
    }
    const reduced = this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    element.scrollIntoView?.({ behavior: reduced ? 'auto' : 'smooth', block: 'center' });
    this.ring(element, null);
    // The click that asked for this is still on its way; the next one removes the ring.
    setTimeout(() =>
      this.document.addEventListener('click', () => this.unring(element), { once: true, capture: true }),
    );
  }

  /** The Overview elements of a figure: the group holding the figure and its "Why?" link. */
  private elements(provId: string): Element[] {
    return Array.from(this.document.querySelectorAll('app-why-link[data-prov-id]'))
      .filter((link) => link.getAttribute('data-prov-id') === provId)
      .map((link) => link.parentElement)
      .filter((e): e is HTMLElement => e !== null);
  }

  private ring(element: Element, ms: number | null): void {
    clearTimeout(this.timers.get(element));
    this.timers.delete(element);
    element.classList.add(RING_CLASS);
    if (ms !== null) {
      this.timers.set(
        element,
        setTimeout(() => this.unring(element), ms),
      );
    }
  }

  private unring(element: Element): void {
    clearTimeout(this.timers.get(element));
    this.timers.delete(element);
    element.classList.remove(RING_CLASS);
  }
}
