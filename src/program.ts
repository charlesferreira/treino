import programJson from './program.json'
import type { DayKey, Program } from './types'

export const program = programJson as unknown as Program

export const DAY_KEYS: DayKey[] = [
  'monday',
  'tuesday',
  'wednesday',
  'thursday',
  'friday',
  'saturday',
  'sunday',
]

export const DAY_NAMES: Record<DayKey, string> = {
  monday: 'Segunda',
  tuesday: 'Terça',
  wednesday: 'Quarta',
  thursday: 'Quinta',
  friday: 'Sexta',
  saturday: 'Sábado',
  sunday: 'Domingo',
}

export function exerciseName(id: string): string {
  return program.exercises[id]?.name ?? id
}

export function exerciseNotes(id: string): string {
  return program.exercises[id]?.notes ?? ''
}
