export type DayKey =
  | 'monday'
  | 'tuesday'
  | 'wednesday'
  | 'thursday'
  | 'friday'
  | 'saturday'
  | 'sunday'

export interface ExerciseDef {
  name: string
  notes: string
}

export interface GymItem {
  exercise: string
  sets: number
  reps: [number, number]
  rir: string
  rest: number
}

export interface GymDay {
  title: string
  type: 'gym'
  items: GymItem[]
}

export interface CardioDay {
  title: string
  type: 'cardio'
  cardio: { minutes: [number, number]; guidance: string }
}

export type ProgramDay = GymDay | CardioDay

export interface Program {
  version: number
  startDate: string
  exercises: Record<string, ExerciseDef>
  week: Record<DayKey, ProgramDay>
}

export interface SetEntry {
  w: number
  r: number
  rir?: number
}

export interface CardioEntry {
  minutes: number
  km?: number
  local?: string
}

export interface DayLog {
  template: DayKey
  sets: Record<string, SetEntry[]>
  cardio?: CardioEntry
  note?: string
}

export type Logs = Record<string, DayLog>
