import seedJson from './program.json'
import { seedUserExercises } from './exercises'
import { loadLogs, saveLogs } from './storage'
import type {
  DayKey,
  ExerciseDef,
  Logs,
  Plan,
  Profile,
  SeedProgram,
  Workout,
  WorkoutItem,
} from './types'

const PLAN_KEY = 'treino.plan'
const PROFILE_KEY = 'treino.profile'
const SNAPSHOT_KEY = 'treino.snapshot.pre-plano'

export const seedProgram = seedJson as unknown as SeedProgram

const DAY_ORDER: DayKey[] = [
  'monday',
  'tuesday',
  'wednesday',
  'thursday',
  'friday',
  'saturday',
  'sunday',
]

/* ---------------------------------- Programa ---------------------------------- */

export function loadPlan(): Plan {
  try {
    const raw = localStorage.getItem(PLAN_KEY)
    if (!raw) return { version: 1, workouts: [] }
    const parsed = JSON.parse(raw) as Plan
    if (!parsed || !Array.isArray(parsed.workouts)) return { version: 1, workouts: [] }
    return parsed
  } catch {
    return { version: 1, workouts: [] }
  }
}

export function savePlan(plan: Plan): void {
  localStorage.setItem(PLAN_KEY, JSON.stringify(plan))
}

export function hasPlan(): boolean {
  return localStorage.getItem(PLAN_KEY) !== null
}

export function findWorkout(id: string | undefined): Workout | undefined {
  if (!id) return undefined
  return loadPlan().workouts.find((w) => w.id === id)
}

export function newWorkoutId(plan: Plan): string {
  const taken = new Set(plan.workouts.map((w) => w.id))
  let n = plan.workouts.length + 1
  while (taken.has(`treino-${n}`)) n++
  return `treino-${n}`
}

export function upsertWorkout(workout: Workout): void {
  const plan = loadPlan()
  const i = plan.workouts.findIndex((w) => w.id === workout.id)
  if (i >= 0) plan.workouts[i] = workout
  else plan.workouts.push(workout)
  savePlan(plan)
}

export function removeWorkout(id: string): void {
  const plan = loadPlan()
  plan.workouts = plan.workouts.filter((w) => w.id !== id)
  savePlan(plan)
}

/** Move um treino na sequência. `delta` −1 sobe, +1 desce. */
export function moveWorkout(id: string, delta: number): void {
  const plan = loadPlan()
  const i = plan.workouts.findIndex((w) => w.id === id)
  const j = i + delta
  if (i < 0 || j < 0 || j >= plan.workouts.length) return
  const [w] = plan.workouts.splice(i, 1)
  plan.workouts.splice(j, 0, w)
  savePlan(plan)
}

export function duplicateWorkout(id: string): string | undefined {
  const plan = loadPlan()
  const i = plan.workouts.findIndex((w) => w.id === id)
  if (i < 0) return undefined
  const copy: Workout = {
    id: newWorkoutId(plan),
    name: `${plan.workouts[i].name} (cópia)`,
    items: plan.workouts[i].items.map((it) => ({ ...it })),
  }
  plan.workouts.splice(i + 1, 0, copy)
  savePlan(plan)
  return copy.id
}

/* ----------------------------------- Perfil ----------------------------------- */

export function loadProfile(): Profile {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    if (!raw) return { name: '' }
    return JSON.parse(raw) as Profile
  } catch {
    return { name: '' }
  }
}

export function saveProfile(p: Profile): void {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(p))
}

/** "Charles Ferreira" → "CF"; "Ana" → "AN". Vazio → "". */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return ''
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/* ---------------------------------- Rotação ----------------------------------- */

function hasEntries(logs: Logs, date: string): boolean {
  const log = logs[date]
  if (!log) return false
  if (Object.values(log.sets).some((s) => s.length > 0)) return true
  return Object.values(log.times ?? {}).some((t) => t.length > 0)
}

/** Treino executado mais recente antes de `date` — a âncora da rotação. */
export function lastExecuted(logs: Logs, date: string): { date: string; workout: string } | undefined {
  const dates = Object.keys(logs)
    .filter((d) => d < date && hasEntries(logs, d))
    .sort()
  const last = dates.at(-1)
  return last ? { date: last, workout: logs[last].template } : undefined
}

/** O próximo da fila: o seguinte ao último executado. Ignora escolha manual de hoje. */
export function nextInSequence(date: string, logs = loadLogs()): Workout | undefined {
  const plan = loadPlan()
  if (plan.workouts.length === 0) return undefined
  const anchor = lastExecuted(logs, date)
  if (!anchor) return plan.workouts[0]
  const i = plan.workouts.findIndex((w) => w.id === anchor.workout)
  if (i < 0) return plan.workouts[0]
  return plan.workouts[(i + 1) % plan.workouts.length]
}

/** O treino de `date`: o escolhido/registrado no dia, ou o próximo da fila. */
export function suggestWorkout(date: string, logs = loadLogs()): Workout | undefined {
  const chosen = logs[date]?.template
  if (chosen) {
    const w = loadPlan().workouts.find((x) => x.id === chosen)
    if (w) return w
  }
  return nextInSequence(date, logs)
}

/* --------------------------- Semeadura e migração ---------------------------- */

function seedWorkouts(): Workout[] {
  const workouts: Workout[] = []
  for (const key of DAY_ORDER) {
    const day = seedProgram.week[key]
    // O id é a chave do dia de propósito: os logs antigos guardam exatamente isso,
    // então o histórico continua apontando para o treino certo sem reescrever nada.
    if (day.type === 'cardio') {
      workouts.push({
        id: key,
        name: day.title,
        items: [
          {
            exercise: 'corrida',
            kind: 'tempo',
            sets: 1,
            minutes: day.cardio.minutes,
            rest: 0,
            notes: day.cardio.guidance,
          },
        ],
      })
    } else {
      const items: WorkoutItem[] = day.items.map((it) => ({
        exercise: it.exercise,
        kind: 'carga',
        sets: it.sets,
        reps: it.reps,
        rir: it.rir,
        rest: it.rest,
      }))
      workouts.push({ id: key, name: day.title, items })
    }
  }
  return workouts
}

function seedExercises(): void {
  const defs: ExerciseDef[] = Object.entries(seedProgram.exercises).map(([id, def]) => ({
    id,
    name: def.name,
    group: 'meus',
    kind: 'carga',
    ...(def.notes ? { notes: def.notes } : {}),
  }))
  seedUserExercises(defs)
}

/** Cardio do formato antigo (um por dia) vira registro de tempo do exercício `corrida`. */
export function migrateLogs(logs: Logs): { logs: Logs; changed: boolean } {
  let changed = false
  for (const log of Object.values(logs)) {
    if (!log.cardio) continue
    const { minutes, km, local } = log.cardio
    log.times ??= {}
    log.times.corrida ??= [{ min: minutes, ...(km !== undefined ? { km } : {}), ...(local ? { local } : {}) }]
    delete log.cardio
    changed = true
  }
  return { logs, changed }
}

/** Guarda uma cópia do estado anterior à migração. Só escreve uma vez. */
function snapshot(): void {
  if (localStorage.getItem(SNAPSHOT_KEY) !== null) return
  localStorage.setItem(
    SNAPSHOT_KEY,
    JSON.stringify({
      logs: localStorage.getItem('treino.logs'),
      settings: localStorage.getItem('treino.settings'),
    }),
  )
}

/** Converte o programa de exemplo em programa editável, preservando ids. */
export function seedFromProgram(): void {
  snapshot()
  seedExercises()
  savePlan({ version: 1, workouts: seedWorkouts() })
  const { logs, changed } = migrateLogs(loadLogs())
  if (changed) saveLogs(logs)
}

/**
 * Decide o que fazer ao abrir o app:
 * - já tem programa → nada;
 * - tem histórico mas não tem programa → migra do programa de exemplo (aparelho antigo);
 * - não tem nada → onboarding (aparelho novo).
 */
export function bootstrap(): { needsOnboarding: boolean } {
  if (hasPlan()) {
    const { logs, changed } = migrateLogs(loadLogs())
    if (changed) saveLogs(logs)
    return { needsOnboarding: false }
  }
  if (Object.keys(loadLogs()).length > 0) {
    seedFromProgram()
    return { needsOnboarding: false }
  }
  return { needsOnboarding: true }
}
