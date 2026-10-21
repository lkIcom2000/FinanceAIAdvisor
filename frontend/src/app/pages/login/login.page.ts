import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SessionService, staffLandingUrl } from '../../core/session.service';
import { BrowserLocation } from '../../core/browser-location';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
})
export class LoginPage {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly location = inject(BrowserLocation);

  /** `?reason=expired` after the backend reported the session as expired. */
  readonly reason = input<string | undefined>();
  protected readonly expired = computed(() => this.reason() === 'expired');

  protected username = '';
  protected password = '';
  protected readonly wrongCredentials = signal(false);
  protected readonly failed = signal(false);
  protected readonly busy = signal(false);

  protected async submit(): Promise<void> {
    const username = this.username.trim();
    if (this.busy() || !username || !this.password) {
      return;
    }
    this.busy.set(true);
    this.wrongCredentials.set(false);
    this.failed.set(false);
    try {
      const user = await this.session.login(username, this.password);
      if (user.role === 'PERSONA') {
        await this.router.navigateByUrl('/');
      } else {
        this.location.assign(staffLandingUrl(user.role));
      }
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.wrongCredentials.set(true);
      } else {
        this.failed.set(true);
      }
    } finally {
      this.busy.set(false);
    }
  }
}
