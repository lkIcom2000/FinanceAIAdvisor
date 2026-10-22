import { formatFigure } from './figure.pipe';

describe('formatFigure', () => {
  it('formats EUR with thousands separators and the backend precision', () => {
    expect(formatFigure('1974.00', 'EUR')).toBe('1,974.00 EUR');
    expect(formatFigure('1240.00', 'EUR')).toBe('1,240.00 EUR');
    expect(formatFigure('1234567.5', 'EUR')).toBe('1,234,567.5 EUR');
    expect(formatFigure('12', 'EUR')).toBe('12 EUR');
  });

  it('uses a real minus sign', () => {
    expect(formatFigure('-90.00', 'EUR')).toBe('−90.00 EUR');
    expect(formatFigure('-1214.00', 'EUR')).toBe('−1,214.00 EUR');
  });

  it('formats the other units without computing', () => {
    expect(formatFigure('0.055', 'FRACTION')).toBe('5.5 %');
    expect(formatFigure('0.05', 'FRACTION')).toBe('5 %');
    expect(formatFigure('1.2', 'FRACTION')).toBe('120 %');
    expect(formatFigure('-0.5', 'PP')).toBe('−0.5 pp');
    expect(formatFigure('3', 'MONTHS')).toBe('3 months');
    expect(formatFigure('1', 'YEARS')).toBe('1 year');
    expect(formatFigure('1250', 'COUNT')).toBe('1,250');
  });
});
