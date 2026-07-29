import type { DayLog, Logs, SetEntry, TimeEntry } from './types'

const LOGS_KEY = 'treino.logs'
const SETTINGS_KEY = 'treino.settings'

export interface Settings {
  sound?: boolean
  lastExport?: string
}

export function loadLogs(): Logs {
  try {
    const raw = localStorage.getItem(LOGS_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return typeof parsed === 'object' && parsed !== null ? (parsed as Logs) : {}
  } catch {
    return {}
  }
}

export function saveLogs(logs: Logs): void {
  localStorage.setItem(LOGS_KEY, JSON.stringify(logs))
}

export function loadSettings(): Settings {
  try {
    return JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? '{}') as Settings
  } catch {
    return {}
  }
}

export function saveSettings(s: Settings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(s))
}

export function getDayLog(date: string): DayLog | undefined {
  return loadLogs()[date]
}

function withLog(logs: Logs, date: string, workout: string): DayLog {
  const log = logs[date] ?? { template: workout, sets: {} }
  logs[date] = log
  return log
}

/** Fixa qual treino é o de hoje (troca manual). Vira a âncora da rotação. */
export function setTemplate(date: string, workout: string): void {
  const logs = loadLogs()
  withLog(logs, date, workout).template = workout
  saveLogs(logs)
}

export function addSet(date: string, workout: string, exercise: string, entry: SetEntry): void {
  const logs = loadLogs()
  const log = withLog(logs, date, workout)
  ;(log.sets[exercise] ??= []).push(entry)
  saveLogs(logs)
}

export function removeSet(date: string, exercise: string, index: number): void {
  const logs = loadLogs()
  const sets = logs[date]?.sets[exercise]
  if (!sets) return
  sets.splice(index, 1)
  if (sets.length === 0) delete logs[date].sets[exercise]
  saveLogs(logs)
}

export function addTime(date: string, workout: string, exercise: string, entry: TimeEntry): void {
  const logs = loadLogs()
  const log = withLog(logs, date, workout)
  log.times ??= {}
  ;(log.times[exercise] ??= []).push(entry)
  saveLogs(logs)
}

export function removeTime(date: string, exercise: string, index: number): void {
  const logs = loadLogs()
  const times = logs[date]?.times?.[exercise]
  if (!times) return
  times.splice(index, 1)
  if (times.length === 0) delete logs[date].times![exercise]
  saveLogs(logs)
}

export function setNote(date: string, workout: string, note: string): void {
  const logs = loadLogs()
  const log = withLog(logs, date, workout)
  if (note.trim()) log.note = note.trim()
  else delete log.note
  saveLogs(logs)
}

/* ----------------------------------- Backup ----------------------------------- */

export interface Backup {
  app: 'treino'
  format: number
  exportedAt: string
  logs: Logs
  settings: Settings
  /** Formato 2: o programa e os exercícios do aparelho vão junto. */
  plan?: unknown
  exercises?: unknown
  profile?: unknown
}

export function exportBackup(todayDate: string): string {
  const backup: Backup = {
    app: 'treino',
    format: 2,
    exportedAt: todayDate,
    logs: loadLogs(),
    settings: loadSettings(),
    plan: JSON.parse(localStorage.getItem('treino.plan') ?? 'null'),
    exercises: JSON.parse(localStorage.getItem('treino.exercises') ?? 'null'),
    profile: JSON.parse(localStorage.getItem('treino.profile') ?? 'null'),
  }
  const s = loadSettings()
  s.lastExport = todayDate
  saveSettings(s)
  return JSON.stringify(backup, null, 2)
}

export interface ImportResult {
  days: number
  hasPlan: boolean
}

/**
 * Aceita backup v2 (com programa), v1 (só logs e settings) ou um objeto de logs cru.
 * Quem chama decide o que fazer quando o backup não traz programa.
 */
export function importBackup(json: string): ImportResult {
  const parsed = JSON.parse(json)
  let logs: Logs
  let hasPlan = false

  if (parsed && typeof parsed === 'object' && 'logs' in parsed) {
    const backup = parsed as Backup
    logs = backup.logs
    if (backup.settings && typeof backup.settings === 'object') saveSettings(backup.settings)
    if (backup.plan && typeof backup.plan === 'object') {
      localStorage.setItem('treino.plan', JSON.stringify(backup.plan))
      hasPlan = true
    }
    if (backup.exercises && typeof backup.exercises === 'object') {
      localStorage.setItem('treino.exercises', JSON.stringify(backup.exercises))
    }
    if (backup.profile && typeof backup.profile === 'object') {
      localStorage.setItem('treino.profile', JSON.stringify(backup.profile))
    }
  } else {
    logs = parsed as Logs
  }

  if (!logs || typeof logs !== 'object' || Array.isArray(logs)) {
    throw new Error('Formato inválido')
  }
  for (const [date, log] of Object.entries(logs)) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || typeof log !== 'object' || log === null) {
      throw new Error(`Registro inválido: ${date}`)
    }
  }
  saveLogs(logs)
  return { days: Object.keys(logs).length, hasPlan }
}
