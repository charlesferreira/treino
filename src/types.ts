export type DayKey =
  | 'monday'
  | 'tuesday'
  | 'wednesday'
  | 'thursday'
  | 'friday'
  | 'saturday'
  | 'sunday'

/** Como o exercício é registrado: carga × reps, ou tempo (e distância opcional). */
export type ItemKind = 'carga' | 'tempo'

export interface ExerciseDef {
  id: string
  name: string
  group: string
  kind?: ItemKind
  aliases?: string[]
  notes?: string
}

export interface WorkoutItem {
  exercise: string
  kind: ItemKind
  sets: number
  /** kind 'carga': faixa de repetições alvo. */
  reps?: [number, number]
  /** kind 'tempo': faixa de minutos alvo. */
  minutes?: [number, number]
  /** Ausente = RIR desligado neste item. */
  rir?: string
  rest: number
  notes?: string
}

export interface Workout {
  id: string
  name: string
  items: WorkoutItem[]
}

/** A ordem de `workouts` É a sequência: o app sugere o seguinte ao último executado. */
export interface Plan {
  version: number
  workouts: Workout[]
}

export interface Profile {
  name: string
}

export interface SetEntry {
  w: number
  r: number
  rir?: number
}

export interface TimeEntry {
  min: number
  km?: number
  local?: string
}

/** Formato antigo: um cardio por dia, fora da lista de exercícios. */
export interface CardioEntry {
  minutes: number
  km?: number
  local?: string
}

export interface DayLog {
  /** Id do treino executado. Nos logs antigos é a chave do dia da semana — e os treinos
   *  semeados a partir do programa de exemplo usam essas mesmas chaves como id. */
  template: string
  sets: Record<string, SetEntry[]>
  times?: Record<string, TimeEntry[]>
  /** Legado: migrado para `times` na primeira execução. */
  cardio?: CardioEntry
  note?: string
}

export type Logs = Record<string, DayLog>

/* ---------- Programa de exemplo (src/program.json), só usado como semente ---------- */

export interface SeedGymItem {
  exercise: string
  sets: number
  reps: [number, number]
  rir: string
  rest: number
}

export interface SeedGymDay {
  title: string
  type: 'gym'
  items: SeedGymItem[]
}

export interface SeedCardioDay {
  title: string
  type: 'cardio'
  cardio: { minutes: [number, number]; guidance: string }
}

export interface SeedProgram {
  version: number
  startDate: string
  exercises: Record<string, { name: string; notes: string }>
  week: Record<DayKey, SeedGymDay | SeedCardioDay>
}
