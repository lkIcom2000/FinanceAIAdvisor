import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { provideApiConfiguration } from './api/api-configuration';
import { sessionExpiredInterceptor } from './core/session-expired.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Same origin as the backend: Spring Security's SPA CSRF uses Angular's XSRF-TOKEN / X-XSRF-TOKEN defaults.
    provideHttpClient(withXsrfConfiguration({}), withInterceptors([sessionExpiredInterceptor])),
    provideApiConfiguration(''),
  ],
};
