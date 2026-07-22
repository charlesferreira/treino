import type { CardioEntry, DayKey, DayLog, Logs, SetEntry } from './types'
import { program } from './program'

const LOGS_KEY = 'treino.logs'
const SETTINGS_KEY = 'treino.settings'

export interface Settings {
  sound?: boolean
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

function saveLogs(logs: Logs): void {
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

export function ensureDayLog(date: string, template: DayKey): DayLog {
  const logs = loadLogs()
  let log = logs[date]
  if (!log) {
    log = { template, sets: {} }
    logs[date] = log
    saveLogs(logs)
  }
  return log
}

export function setTemplate(date: string, template: DayKey): void {
  const logs = loadLogs()
  const log = logs[date] ?? { template, sets: {} }
  log.template = template
  logs[date] = log
  saveLogs(logs)
}

export function addSet(date: string, template: DayKey, exercise: string, entry: SetEntry): void {
  const logs = loadLogs()
  const log = logs[date] ?? { template, sets: {} }
  ;(log.sets[exercise] ??= []).push(entry)
  logs[date] = log
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

export function setCardio(date: string, template: DayKey, cardio: CardioEntry): void {
  const logs = loadLogs()
  const log = logs[date] ?? { template, sets: {} }
  log.cardio = cardio
  logs[date] = log
  saveLogs(logs)
}

export function setNote(date: string, template: DayKey, note: string): void {
  const logs = loadLogs()
  const log = logs[date] ?? { template, sets: {} }
  if (note.trim()) log.note = note.trim()
  else delete log.note
  logs[date] = log
  saveLogs(logs)
}

export interface Backup {
  app: 'treino'
  exportedAt: string
  programVersion: number
  logs: Logs
  settings: Settings
}

export function exportBackup(todayDate: string): string {
  const backup: Backup = {
    app: 'treino',
    exportedAt: todayDate,
    programVersion: program.version,
    logs: loadLogs(),
    settings: loadSettings(),
  }
  return JSON.stringify(backup, null, 2)
}

/** Aceita o formato de backup ou um objeto de logs cru. Retorna o nº de dias importados. */
export function importBackup(json: string): number {
  const parsed = JSON.parse(json)
  let logs: Logs
  if (parsed && typeof parsed === 'object' && 'logs' in parsed) {
    logs = (parsed as Backup).logs
    if (parsed.settings && typeof parsed.settings === 'object') {
      saveSettings(parsed.settings as Settings)
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
  return Object.keys(logs).length
}
