import { HttpTestingController } from '@angular/common/http/testing';
import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { BrowserLocation } from './browser-location';
import { workspaceGuard } from './workspace.guard';
import { FakeBrowserLocation, answerSessionLoad, httpTestingProviders } from '../testing/session-testing';

describe('workspaceGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...httpTestingProviders()] });
  });

  function run(): Promise<boolean | UrlTree> {
    return runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
      workspaceGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Promise<boolean | UrlTree>;
  }

  it('lets a persona in', async () => {
    const result = run();
    await answerSessionLoad({ role: 'PERSONA', displayName: 'Lena' });
    expect(await result).toBe(true);
  });

  it('sends a visitor without session to Login', async () => {
    const result = run();
    await answerSessionLoad(null);
    expect(String(await result)).toBe('/login');
  });

  it('sends the researcher to the research Swagger UI group instead of the workspace', async () => {
    const result = run();
    await answerSessionLoad({ role: 'RESEARCHER', displayName: 'Researcher' });
    expect(await result).toBe(false);
    const location = TestBed.inject(BrowserLocation) as unknown as FakeBrowserLocation;
    expect(location.assigned).toEqual(['/swagger-ui/index.html?urls.primaryName=research']);
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());
});
