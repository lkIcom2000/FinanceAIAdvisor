import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { firstValueFrom } from 'rxjs';
import { ProvenanceService } from '../api/services/provenance.service';
import { ProvenanceResponse } from '../api/models/provenance-response';
import { FigurePipe } from './figure.pipe';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** An ISO date as "1 Oct 2026", read from the text (no time zone involved). */
function formatDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${Number(day)} ${MONTHS[Number(month) - 1]} ${year}`;
}

export interface WhyDialogData {
  provId: string;
}

/**
 * "Why?" dialog (FR-8): where a figure comes from, what went in, assumptions and checks, all from the provenance
 * endpoint. An input's "Why?" replaces the content in place; "Back" restores the previous figure.
 */
@Component({
  selector: 'app-why-dialog',
  imports: [MatDialogModule, MatButtonModule, FigurePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './why-dialog.component.html',
  styleUrl: './why-dialog.component.scss',
})
export class WhyDialogComponent {
  private readonly provenance = inject(ProvenanceService);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  /** The figures opened in this dialog; the last one is shown. */
  private readonly stack = signal<ProvenanceResponse[]>([]);
  protected readonly current = computed(() => this.stack().at(-1));
  protected readonly canGoBack = computed(() => this.stack().length > 1);
  /** True while the initial figure or a drill-down is loading. */
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  constructor() {
    void this.open(inject<WhyDialogData>(MAT_DIALOG_DATA).provId);
  }

  /** Loads a figure and shows it on top of the stack; clicks while a figure is loading are ignored. */
  protected async open(provId: string): Promise<void> {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    try {
      const figure = await firstValueFrom(this.provenance.show({ provId }));
      this.stack.update((s) => [...s, figure]);
      this.focusHeading();
    } catch {
      // Without a figure the error replaces the content; during a drill-down it shows inline.
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  protected back(): void {
    if (this.loading()) {
      return;
    }
    this.failed.set(false);
    this.stack.update((s) => s.slice(0, -1));
    this.focusHeading();
  }

  /** "valid from 1 Jan 2026 to 31 Dec 2026"; missing dates are left out. */
  protected validity(from?: string, to?: string): string {
    const parts = [from && `from ${formatDate(from)}`, to && `to ${formatDate(to)}`].filter(
      Boolean,
    );
    return parts.length ? `, valid ${parts.join(' ')}` : '';
  }

  /** After the content changed in place, keyboard and screen-reader users continue at the new title. */
  private focusHeading(): void {
    queueMicrotask(() => this.heading()?.nativeElement.focus());
  }
}
