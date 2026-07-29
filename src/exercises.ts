import catalogJson from './exercises.json'
import type { ExerciseDef, ItemKind } from './types'

const USER_KEY = 'treino.exercises'

export const CATALOG = catalogJson as ExerciseDef[]

export const GROUPS: [string, string][] = [
  ['peito', 'Peito'],
  ['costas', 'Costas'],
  ['ombros', 'Ombros'],
  ['biceps', 'Bíceps'],
  ['triceps', 'Tríceps'],
  ['antebraco', 'Antebraço'],
  ['quadriceps', 'Quadríceps'],
  ['posteriores', 'Posteriores'],
  ['gluteos', 'Glúteos'],
  ['panturrilha', 'Panturrilha'],
  ['abdomen', 'Abdômen'],
  ['cardio', 'Cardio'],
  ['corpo-inteiro', 'Corpo inteiro'],
]

export function groupLabel(group: string): string {
  return GROUPS.find(([g]) => g === group)?.[1] ?? 'Meus exercícios'
}

/** Exercícios criados/importados neste aparelho. Sobrepõem o catálogo quando o id coincide. */
export function loadUserExercises(): Record<string, ExerciseDef> {
  try {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, ExerciseDef>) : {}
  } catch {
    return {}
  }
}

export function saveUserExercises(map: Record<string, ExerciseDef>): void {
  localStorage.setItem(USER_KEY, JSON.stringify(map))
}

/** Catálogo embutido + exercícios do aparelho, com o do aparelho ganhando no empate de id. */
export function allExercises(): ExerciseDef[] {
  const user = loadUserExercises()
  const merged = new Map<string, ExerciseDef>()
  for (const ex of CATALOG) merged.set(ex.id, ex)
  for (const ex of Object.values(user)) merged.set(ex.id, ex)
  return [...merged.values()]
}

export function findExercise(id: string): ExerciseDef | undefined {
  return loadUserExercises()[id] ?? CATALOG.find((e) => e.id === id)
}

export function exerciseName(id: string): string {
  return findExercise(id)?.name ?? id
}

export function exerciseNotes(id: string): string {
  return findExercise(id)?.notes ?? ''
}

export function exerciseKind(id: string): ItemKind {
  return findExercise(id)?.kind ?? 'carga'
}

/** Minúsculas, sem acento — para busca e para gerar id. */
export function normalize(s: string): string {
  return s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
}

export function slugify(name: string): string {
  return normalize(name).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'exercicio'
}

/** Busca por nome e sinônimos; nome que começa com o termo vem antes. */
export function searchExercises(query: string): ExerciseDef[] {
  const all = allExercises()
  const q = normalize(query)
  if (!q) return all
  const scored: [number, ExerciseDef][] = []
  for (const ex of all) {
    const name = normalize(ex.name)
    const hay = [name, ...(ex.aliases ?? []).map(normalize)]
    const starts = hay.some((h) => h.startsWith(q))
    const has = hay.some((h) => h.includes(q))
    if (starts) scored.push([0, ex])
    else if (has) scored.push([1, ex])
  }
  return scored.sort((a, b) => a[0] - b[0] || a[1].name.localeCompare(b[1].name, 'pt-BR')).map(([, ex]) => ex)
}

/** Cria (ou reaproveita) um exercício digitado à mão. Retorna o id. */
export function createExercise(name: string, kind: ItemKind = 'carga'): string {
  const clean = name.trim()
  const existing = allExercises().find((e) => normalize(e.name) === normalize(clean))
  if (existing) return existing.id

  const user = loadUserExercises()
  const taken = new Set([...CATALOG.map((e) => e.id), ...Object.keys(user)])
  const base = slugify(clean)
  let id = base
  let n = 2
  while (taken.has(id)) id = `${base}-${n++}`

  user[id] = { id, name: clean, group: 'meus', kind }
  saveUserExercises(user)
  return id
}

/** Usado na migração do programa de exemplo: grava definições preservando os ids. */
export function seedUserExercises(defs: ExerciseDef[]): void {
  const user = loadUserExercises()
  for (const def of defs) user[def.id] = def
  saveUserExercises(user)
}
