import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { AppHeaderComponent } from '../../shell/app-header.component';
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
 * of the Overview column for the session (the tabs keep their content, so nothing reloads on a switch).
 */
@Component({
  selector: 'app-workspace-page',
  imports: [
    AppHeaderComponent,
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

  readonly tab = input<string | undefined>();

  protected readonly tabs = OVERVIEW_TABS;
  protected readonly selectedIndex = computed(() =>
    Math.max(
      0,
      OVERVIEW_TABS.findIndex((t) => t.key === this.tab()),
    ),
  );

  private readonly overview = viewChild.required<ElementRef<HTMLElement>>('overview');
  private readonly scrollPositions = new Map<OverviewTab, number>();
  private shownTab: OverviewTab | null = null;

  protected select(index: number): void {
    const scroller = this.overview().nativeElement;
    this.scrollPositions.set(this.shownTab ?? OVERVIEW_TABS[this.selectedIndex()].key, scroller.scrollTop);
    this.shownTab = OVERVIEW_TABS[index].key;
    void this.router.navigate([], {
      queryParams: { tab: OVERVIEW_TABS[index].key },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** After the tab switch has settled, return to the scroll position the shown tab had (top on its first visit). */
  protected restoreScroll(): void {
    const tab = this.shownTab ?? OVERVIEW_TABS[this.selectedIndex()].key;
    this.overview().nativeElement.scrollTop = this.scrollPositions.get(tab) ?? 0;
  }
}
