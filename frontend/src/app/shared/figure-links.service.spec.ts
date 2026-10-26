import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { httpTestingProviders } from '../testing/session-testing';
import { FigureLinksService, RING_CLASS, RING_MS, provIdsIn } from './figure-links.service';

describe('FigureLinksService', () => {
  let links: FigureLinksService;
  let http: HttpTestingController;
  const added: HTMLElement[] = [];

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [...httpTestingProviders()] });
    links = TestBed.inject(FigureLinksService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    added.forEach((e) => e.remove());
    added.length = 0;
    http.verify();
    vi.useRealTimers();
  });

  /** An Overview figure group with its "Why?" link, as the tabs render it. */
  function overviewFigure(provId: string): HTMLElement {
    const group = document.createElement('div');
    const link = document.createElement('app-why-link');
    link.setAttribute('data-prov-id', provId);
    group.appendChild(link);
    document.body.appendChild(group);
    added.push(group);
    return group;
  }

  function index(): void {
    links.refresh().subscribe();
    http.expectOne('/api/overview/home').flush({ currentAccount: { provId: 'home0000000000a1' } });
    http.expectOne('/api/overview/liquidity').flush({ forecast: { provId: 'liq0000000000a2' } });
    http
      .expectOne('/api/overview/wealth')
      .flush({ holdings: [{ value: { provId: 'wea0000000000a3' } }] });
  }

  it('finds every provId in a response', () => {
    expect([...provIdsIn({ a: { provId: 'x' }, b: [{ provId: 'y' }, { c: { provId: 'z' } }] })]).toEqual([
      'x',
      'y',
      'z',
    ]);
  });

  it('indexes the figures of all three tabs, also tabs never opened', () => {
    index();

    expect(links.index().get('home0000000000a1')).toBe('home');
    expect(links.index().get('wea0000000000a3')).toBe('wealth');
    expect(links.isInOverview('liq0000000000a2')).toBe(true);
    expect(links.isInOverview('calc000000000a9')).toBe(false);
  });

  it('rings a figure on the shown tab for 4 s and dots another tab without switching', () => {
    index();
    const home = overviewFigure('home0000000000a1');
    links.shown('home');

    links.highlight(['home0000000000a1', 'wea0000000000a3', 'calc000000000a9']);

    expect(home.classList).toContain(RING_CLASS);
    expect([...links.dots()]).toEqual(['wealth']);
    expect(links.activeTab()).toBe('home');
    vi.advanceTimersByTime(RING_MS);
    expect(home.classList).not.toContain(RING_CLASS);

    links.shown('wealth');
    expect(links.dots().size).toBe(0);
  });

  it('"Show in overview" asks for the tab, then scrolls and holds the ring until the next click', () => {
    index();
    links.shown('home');
    links.show('wea0000000000a3');
    expect(links.requested()).toEqual({ tab: 'wealth', provId: 'wea0000000000a3' });

    const wealth = overviewFigure('wea0000000000a3');
    const scrolled = vi.fn();
    wealth.scrollIntoView = scrolled;
    links.reveal();

    expect(links.requested()).toBeNull();
    expect(scrolled).toHaveBeenCalled();
    vi.advanceTimersByTime(RING_MS * 2);
    expect(wealth.classList).toContain(RING_CLASS);
    document.body.click();
    expect(wealth.classList).not.toContain(RING_CLASS);
  });
});
