import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { AppHeaderComponent } from '../../shell/app-header.component';
import { HomeTabComponent } from './home-tab.component';

/** The Overview tabs; the active one lives in `?tab=` so reloads and deep links keep it. */
export const OVERVIEW_TABS = [
  { key: 'home', label: 'Home' },
  { key: 'liquidity', label: 'Liquidity' },
  { key: 'wealth', label: 'Wealth & Retirement' },
  { key: 'letters', label: 'Letters' },
] as const;

export type OverviewTab = (typeof OVERVIEW_TABS)[number]['key'];

/** Workspace shell: Financial Overview on the left, chat column on the right. */
@Component({
  selector: 'app-workspace-page',
  imports: [AppHeaderComponent, HomeTabComponent, MatTabsModule],
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

  protected select(index: number): void {
    void this.router.navigate([], {
      queryParams: { tab: OVERVIEW_TABS[index].key },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
