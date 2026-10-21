import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { BrowserLocation } from '../core/browser-location';
import { sessionExpiredInterceptor } from '../core/session-expired.interceptor';

/** Records full-page navigations instead of leaving the test page. */
export class FakeBrowserLocation {
  readonly assigned: string[] = [];
  assign(url: string): void {
    this.assigned.push(url);
  }
}

export function httpTestingProviders(): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(withXsrfConfiguration({}), withInterceptors([sessionExpiredInterceptor])),
    provideHttpClientTesting(),
    provideApiConfiguration(''),
    { provide: BrowserLocation, useClass: FakeBrowserLocation },
  ];
}

/** Answers the CSRF call and then `me` (or a 401 when `me` is null). */
export async function answerSessionLoad(me: { role: string; displayName: string } | null): Promise<void> {
  const http = TestBed.inject(HttpTestingController);
  await flushMicrotasks();
  http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
  await flushMicrotasks();
  const request = http.expectOne('/api/auth/me');
  if (me === null) {
    request.flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
  } else {
    request.flush(me);
  }
  await flushMicrotasks();
}

export async function flushMicrotasks(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve();
  }
}
