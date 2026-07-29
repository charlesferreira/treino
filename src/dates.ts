import type { DayKey } from './types'
import { DAY_KEYS } from './program'

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function toDateKey(d: Date): string {
  // Sempre no fuso local — nunca toISOString(), que vira o dia em UTC.
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Data "de hoje", com override via ?d=YYYY-MM-DD para testes. */
export function todayKey(): string {
  const forced = new URLSearchParams(location.search).get('d')
  if (forced && /^\d{4}-\d{2}-\d{2}$/.test(forced)) return forced
  return toDateKey(new Date())
}

export function dateFromKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function weekdayOf(key: string): DayKey {
  const js = dateFromKey(key).getDay() // 0 = domingo
  return DAY_KEYS[(js + 6) % 7]
}

const WEEKDAY_SHORT = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']

export function formatShort(key: string): string {
  const d = dateFromKey(key)
  return `${WEEKDAY_SHORT[d.getDay()]} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}`
}

export function formatDayMonth(key: string): string {
  const d = dateFromKey(key)
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`
}

export function weekdayShort(key: string): string {
  return WEEKDAY_SHORT[dateFromKey(key).getDay()]
}

export function dayNumber(key: string): string {
  return pad(dateFromKey(key).getDate())
}

const MONTHS = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
]

/** "julho de 2026" — cabeçalho de grupo no histórico. */
export function monthLabel(key: string): string {
  const d = dateFromKey(key)
  return `${MONTHS[d.getMonth()]} de ${d.getFullYear()}`
}
