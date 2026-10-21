import { Injectable } from '@angular/core';

/** Full-page navigation (to the backend-served Swagger UI); a seam so tests need not leave the page. */
@Injectable({ providedIn: 'root' })
export class BrowserLocation {
  assign(url: string): void {
    window.location.assign(url);
  }
}
