import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { SessionService } from '../core/session.service';

/** Navy app header: wordmark, divider, product name, logged-in name, Log out. */
@Component({
  selector: 'app-header',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="brand-bar">
      <span class="wordmark">Atruvia</span>
      <span class="divider" aria-hidden="true"></span>
      <span class="product">Fine</span>
      <span class="spacer"></span>
      <span class="user">{{ session.user()?.displayName }}</span>
      <button mat-button class="logout" type="button" (click)="logout()">Log out</button>
    </header>
  `,
  styles: `
    .spacer {
      flex: 1;
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

  protected async logout(): Promise<void> {
    await this.session.logout();
    await this.router.navigateByUrl('/login');
  }
}
