import { Pipe, PipeTransform } from '@angular/core';
import { Figure } from '../api/models/figure';

export type FigureUnit = Figure['unit'];

/** Real minus sign (U+2212) for negative figures. */
export const MINUS = '−';

/**
 * Formats a backend figure for display. It only formats the decimal string the backend sent: it never rounds,
 * never changes the precision and never computes, so one figure key always shows one value.
 */
export function formatFigure(value: string, unit: FigureUnit): string {
  const negative = value.startsWith('-');
  const digits = negative ? value.slice(1) : value;
  const sign = negative ? MINUS : '';
  switch (unit) {
    case 'EUR':
      return `${sign}${group(digits)} EUR`;
    case 'FRACTION':
      return `${sign}${group(shiftTwoPlaces(digits))} %`;
    case 'PP':
      return `${sign}${group(digits)} pp`;
    case 'MONTHS':
      return `${sign}${group(digits)} ${isOne(digits) ? 'month' : 'months'}`;
    case 'YEARS':
      return `${sign}${group(digits)} ${isOne(digits) ? 'year' : 'years'}`;
    case 'COUNT':
      return `${sign}${group(digits)}`;
  }
}

/** Whether the decimal text equals one ("1", "1.0", "01.00"), compared as text. */
function isOne(digits: string): boolean {
  return /^0*1(\.0*)?$/.test(digits);
}

/** Thousands separators on the integer part: "1974.00" becomes "1,974.00". */
function group(digits: string): string {
  const [integer, fraction] = digits.split('.');
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return fraction === undefined ? grouped : `${grouped}.${fraction}`;
}

/** A share as a percentage by moving the decimal point (text only, no arithmetic): "0.055" becomes "5.5". */
function shiftTwoPlaces(digits: string): string {
  const [integer, fraction = ''] = digits.split('.');
  const padded = fraction.padEnd(2, '0');
  const whole = (integer + padded.slice(0, 2)).replace(/^0+(?=\d)/, '');
  const rest = padded.slice(2);
  return rest ? `${whole}.${rest}` : whole;
}

@Pipe({ name: 'figure' })
export class FigurePipe implements PipeTransform {
  transform(value: string, unit: FigureUnit): string {
    return formatFigure(value, unit);
  }
}
