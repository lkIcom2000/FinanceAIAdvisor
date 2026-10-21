import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../api/services/auth.service';
import { SessionInfo } from '../api/models/session-info';

/** Where advisor and researcher accounts land: their Swagger UI group (no advisor or research UI). */
export function staffLandingUrl(role: SessionInfo['role']): string {
  return `/swagger-ui/index.html?urls.primaryName=${role === 'ADVISOR' ? 'advisor' : 'research'}`;
}

/** The logged-in account, as reported by the backend. The frontend never decides who someone is. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly auth = inject(AuthService);
  private readonly current = signal<SessionInfo | null>(null);
  private loaded: Promise<SessionInfo | null> | null = null;

  readonly user = this.current.asReadonly();
  readonly isPersona = computed(() => this.current()?.role === 'PERSONA');

  /** Loads the session once: issues the CSRF cookie, then asks who is logged in. */
  load(): Promise<SessionInfo | null> {
    this.loaded ??= this.fetch();
    return this.loaded;
  }

  async login(username: string, password: string): Promise<SessionInfo> {
    await firstValueFrom(this.auth.csrf());
    const info = await firstValueFrom(this.auth.login({ body: { username, password } }));
    this.current.set(info);
    this.loaded = Promise.resolve(info);
    return info;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.auth.logout());
    } finally {
      this.clear();
    }
  }

  /** Forgets the session locally, e.g. after the backend reported it expired. */
  clear(): void {
    this.current.set(null);
    this.loaded = Promise.resolve(null);
  }

  private async fetch(): Promise<SessionInfo | null> {
    try {
      await firstValueFrom(this.auth.csrf());
      const info = await firstValueFrom(this.auth.me());
      this.current.set(info);
      return info;
    } catch {
      this.current.set(null);
      return null;
    }
  }
}
