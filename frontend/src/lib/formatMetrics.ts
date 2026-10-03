import type { Locale } from '../i18n/I18nProvider'

export function formatPercent(value: number | null, locale: Locale): string {
  if (value == null) return '—'
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(value / 100)
}

export function formatHours(value: number | null, locale: Locale): string {
  if (value == null) return '—'
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(value)
}
