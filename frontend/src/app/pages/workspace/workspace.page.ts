import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatTabGroup, MatTabsModule } from '@angular/material/tabs';
import { FigureLinksService } from '../../shared/figure-links.service';
import { NotificationsService } from '../../shared/notifications.service';
import { AppHeaderComponent } from '../../shell/app-header.component';
import { ChatColumnComponent } from './chat-column.component';
import { HomeTabComponent } from './home-tab.component';
import { LiquidityTabComponent } from './liquidity-tab.component';
import { WealthTabComponent } from './wealth-tab.component';

/** The Overview tabs; the active one lives in `?tab=` so reloads and deep links keep it. */
export const OVERVIEW_TABS = [
  { key: 'home', label: 'Home' },
  { key: 'liquidity', label: 'Liquidity' },
  { key: 'wealth', label: 'Wealth & Retirement' },
  { key: 'letters', label: 'Letters' },
] as const;

export type OverviewTab = (typeof OVERVIEW_TABS)[number]['key'];

/**
 * Workspace shell: Financial Overview on the left, chat column on the right. Each tab keeps its own scroll position
 * of the Overview column for the session (the tabs keep their content, so nothing reloads on a switch). A tab label
 * shows a cyan dot while the tab holds a figure a chat answer referenced; it switches only on "Show in overview".
 */
@Component({
  selector: 'app-workspace-page',
  imports: [
    AppHeaderComponent,
    ChatColumnComponent,
    HomeTabComponent,
    LiquidityTabComponent,
    WealthTabComponent,
    MatTabsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './workspace.page.html',
  styleUrl: './workspace.page.scss',
})
export class WorkspacePage {
  private readonly router = inject(Router);
  protected readonly links = inject(FigureLinksService);
  protected readonly notifications = inject(NotificationsService);

  readonly tab = input<string | undefined>();

  protected readonly tabs = OVERVIEW_TABS;
  protected readonly selectedIndex = computed(() =>
    Math.max(
      0,
      OVERVIEW_TABS.findIndex((t) => t.key === this.tab()),
    ),
  );

  private readonly overview = viewChild.required<ElementRef<HTMLElement>>('overview');
  private readonly tabGroup = viewChild.required(MatTabGroup);
  private readonly scrollPositions = new Map<OverviewTab, number>();
  /** The index of the tab whose content is on screen, read from the tab group once a switch has settled. */
  private shownIndex = 0;

  constructor() {
    // The Home list and the tab badge share one poll for as long as the workspace is open.
    this.notifications.start();
    inject(DestroyRef).onDestroy(() => this.notifications.stop());
    afterNextRender(() => {
      this.shownIndex = this.tabGroup().selectedIndex ?? 0;
      this.links.shown(OVERVIEW_TABS[this.shownIndex].key);
    });
    // "Show in overview": switch to the figure's tab (the reveal follows when the switch settles), or reveal at once.
    effect(() => {
      const request = this.links.requested();
      if (!request) {
        return;
      }
      const index = OVERVIEW_TABS.findIndex((t) => t.key === request.tab);
      if (index === this.shownIndex) {
        this.links.reveal();
      } else {
        this.select(index);
      }
    });
  }

  /** Runs on every index change of the tab group, from a click, from `?tab=` or from "Show in overview". */
  protected select(index: number): void {
    this.scrollPositions.set(OVERVIEW_TABS[this.shownIndex].key, this.overview().nativeElement.scrollTop);
    void this.router.navigate([], {
      queryParams: { tab: OVERVIEW_TABS[index].key },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** After the tab switch has settled, return to the scroll position the shown tab had (top on its first visit). */
  protected restoreScroll(): void {
    this.shownIndex = this.tabGroup().selectedIndex ?? 0;
    this.overview().nativeElement.scrollTop =
      this.scrollPositions.get(OVERVIEW_TABS[this.shownIndex].key) ?? 0;
    this.links.shown(OVERVIEW_TABS[this.shownIndex].key);
    if (this.links.requested()?.tab === OVERVIEW_TABS[this.shownIndex].key) {
      this.links.reveal();
    }
  }
}
