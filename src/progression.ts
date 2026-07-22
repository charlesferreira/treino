import type { GymItem, Logs, SetEntry } from './types'

export interface Session {
  date: string
  sets: SetEntry[]
}

export type ProgressionState =
  | { kind: 'primeira-vez' }
  | { kind: 'subir-carga'; last: Session; plateau: boolean }
  | { kind: 'somar-reps'; last: Session; plateau: boolean }

/** Sessões anteriores (excluindo `excludeDate`) que contêm o exercício, da mais recente para a mais antiga. */
export function sessionsFor(exercise: string, logs: Logs, excludeDate: string): Session[] {
  return Object.keys(logs)
    .filter((date) => date !== excludeDate && (logs[date].sets[exercise]?.length ?? 0) > 0)
    .sort()
    .reverse()
    .map((date) => ({ date, sets: logs[date].sets[exercise] }))
}

/** "1–2" → 2, "2" → 2, "0–1" → 1. Alvo é o teto da faixa de RIR. */
export function rirTarget(rir: string): number {
  const nums = rir.match(/\d+/g)
  if (!nums || nums.length === 0) return Infinity
  return Math.max(...nums.map(Number))
}

function maxWeight(sets: SetEntry[]): number {
  return Math.max(...sets.map((s) => s.w))
}

function totalReps(sets: SetEntry[]): number {
  return sets.reduce((acc, s) => acc + s.r, 0)
}

/** 3 últimas sessões sem aumento de carga máxima nem de reps totais entre sessões consecutivas. */
function isPlateau(sessions: Session[]): boolean {
  if (sessions.length < 3) return false
  const [s3, s2, s1] = sessions // s3 = mais recente
  const improved = (older: Session, newer: Session) =>
    maxWeight(newer.sets) > maxWeight(older.sets) || totalReps(newer.sets) > totalReps(older.sets)
  return !improved(s1, s2) && !improved(s2, s3)
}

export function computeProgression(
  exercise: string,
  item: GymItem,
  logs: Logs,
  today: string,
): ProgressionState {
  const sessions = sessionsFor(exercise, logs, today)
  if (sessions.length === 0) return { kind: 'primeira-vez' }

  const last = sessions[0]
  const topo = item.reps[1]
  const alvo = rirTarget(item.rir)
  const hitTop = last.sets.every(
    (s) => s.r >= topo && (s.rir === undefined || s.rir <= alvo),
  )
  const plateau = isPlateau(sessions)
  return hitTop ? { kind: 'subir-carga', last, plateau } : { kind: 'somar-reps', last, plateau }
}
