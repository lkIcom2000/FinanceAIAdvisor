import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { PersonaService } from '../api/services/persona.service';
import { SessionService } from '../core/session.service';

/**
 * Navy app header: wordmark, divider, product name, the simulated-date chip (research profile only: the backend
 * sends `today` only then), logged-in name, Log out.
 */
@Component({
  selector: 'app-header',
  imports: [DatePipe, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="brand-bar">
      <span class="wordmark">Atruvia</span>
      <span class="divider" aria-hidden="true"></span>
      <span class="product">Fine</span>
      <!-- A date-only ISO string is parsed as local midnight, so it must be formatted in local time too. -->
      @if (persona()?.today; as today) {
        <span class="date-chip">Today: {{ today | date: 'd MMM y' }}</span>
      }
      <span class="spacer"></span>
      <span class="user">{{ session.user()?.displayName }}</span>
      <button mat-button class="logout" type="button" (click)="logout()">Log out</button>
    </header>
  `,
  styles: `
    .spacer {
      flex: 1;
    }
    .date-chip {
      padding: 4px 12px;
      border-radius: 9999px;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--fine-label);
    }
    .user {
      font: var(--fine-label);
      margin-right: 8px;
    }
    .logout {
      --mat-button-text-label-text-color: #ffffff;
    }
  `,
})
export class AppHeaderComponent {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);
  protected readonly persona = toSignal(inject(PersonaService).current().pipe(catchError(() => of(null))));

  protected async logout(): Promise<void> {
    await this.session.logout();
    await this.router.navigateByUrl('/login');
  }
}
