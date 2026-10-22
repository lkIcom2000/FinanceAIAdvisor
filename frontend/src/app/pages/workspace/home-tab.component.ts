import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith } from 'rxjs';
import { OverviewService } from '../../api/services/overview.service';
import { HomeOverview } from '../../api/models/home-overview';
import { FigurePipe } from '../../shared/figure.pipe';
import { WhyLinkComponent } from '../../shared/why-link.component';

type HomeState =
  { status: 'loading' } | { status: 'failed' } | { status: 'ready'; home: HomeOverview };

/** Home tab: the summary figures, each from the backend with its figure key and a "Why?" link. */
@Component({
  selector: 'app-home-tab',
  imports: [FigurePipe, WhyLinkComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home-tab.component.html',
  styleUrl: './home-tab.component.scss',
})
export class HomeTabComponent {
  protected readonly state = toSignal(
    inject(OverviewService)
      .home()
      .pipe(
        map((home): HomeState => ({ status: 'ready', home })),
        catchError(() => of<HomeState>({ status: 'failed' })),
        startWith<HomeState>({ status: 'loading' }),
      ),
    { requireSync: true },
  );
}
