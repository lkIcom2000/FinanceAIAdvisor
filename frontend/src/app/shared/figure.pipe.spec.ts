import { formatFigure, fractionToPercent, percentToFraction } from './figure.pipe';

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
    expect(formatFigure('1.0', 'MONTHS')).toBe('1.0 month');
    expect(formatFigure('1.00', 'YEARS')).toBe('1.00 year');
    expect(formatFigure('1.5', 'MONTHS')).toBe('1.5 months');
    expect(formatFigure('-1', 'MONTHS')).toBe('−1 month');
    expect(formatFigure('1250', 'COUNT')).toBe('1,250');
    expect(formatFigure('1', 'FLAG')).toBe('Yes');
    expect(formatFigure('1.00', 'FLAG')).toBe('Yes');
    expect(formatFigure('0', 'FLAG')).toBe('No');
  });
});

describe('percentage fields', () => {
  it('shows a share as a percentage and reads a typed percentage back as a share, as text', () => {
    expect(fractionToPercent('0.04')).toBe('4');
    expect(fractionToPercent('0.045')).toBe('4.5');
    expect(fractionToPercent('-0.02')).toBe('-2');
    expect(percentToFraction('4')).toBe('0.04');
    expect(percentToFraction('4.5')).toBe('0.045');
    expect(percentToFraction('12')).toBe('0.12');
    expect(percentToFraction('120')).toBe('1.2');
    expect(percentToFraction('-2')).toBe('-0.02');
    expect(percentToFraction('0')).toBe('0');
    expect(percentToFraction('3.50')).toBe('0.035');
  });
});
