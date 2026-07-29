import {
  GROUPS,
  createExercise,
  exerciseKind,
  exerciseName,
  groupLabel,
  normalize,
  searchExercises,
} from '../exercises'
import type { ItemKind, WorkoutItem } from '../types'
import { button, el, fmtRest, icon, overlay } from '../ui'

/** Sheet de uma linha de texto (nome do treino, nome do usuário…). */
export function openTextSheet(
  title: string,
  value: string,
  placeholder: string,
  onSave: (v: string) => void,
): void {
  const input = el('input', 'text-input') as HTMLInputElement
  input.type = 'text'
  input.value = value
  input.placeholder = placeholder
  let close: () => void
  const save = button('primary-btn', 'Salvar', () => {
    const v = input.value.trim()
    if (!v) return
    close()
    onSave(v)
  })
  input.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter') save.click()
  })
  close = overlay(el('div', 'sheet-list', [el('h2', 'sheet-title', title), input, save]))
  setTimeout(() => input.focus(), 60)
}

/** Busca de exercício: catálogo + os do aparelho, aceitando texto livre. */
export function openExercisePicker(onPick: (exerciseId: string) => void): void {
  const search = el('input', 'search-input') as HTMLInputElement
  search.type = 'search'
  search.placeholder = 'Buscar exercício…'
  search.autocomplete = 'off'

  const results = el('div', 'picker-results')
  let close: () => void

  const pick = (id: string) => {
    close()
    onPick(id)
  }

  const draw = () => {
    const q = search.value.trim()
    results.innerHTML = ''

    const found = searchExercises(q)
    const exact = found.some((e) => normalize(e.name) === normalize(q))
    if (q && !exact) {
      results.append(button('picker-new', [
        icon('plus', 18),
        el('span', '', [el('b', '', `Usar “${q}”`), el('span', 'picker-hint', 'criar exercício')]),
      ], () => pick(createExercise(q))))
    }

    const order = ['meus', ...GROUPS.map(([g]) => g)]
    const byGroup = new Map<string, typeof found>()
    for (const ex of found) {
      const g = order.includes(ex.group) ? ex.group : 'meus'
      const arr = byGroup.get(g) ?? []
      arr.push(ex)
      byGroup.set(g, arr)
    }
    for (const g of order) {
      const list = byGroup.get(g)
      if (!list?.length) continue
      results.append(el('div', 'picker-group', groupLabel(g)))
      for (const ex of list) {
        results.append(button('picker-item', [
          el('span', '', ex.name),
          ex.kind === 'tempo' ? el('span', 'picker-tag', 'tempo') : null,
        ], () => pick(ex.id)))
      }
    }
    if (!found.length && !q) results.append(el('p', 'empty', 'Nenhum exercício.'))
  }

  search.addEventListener('input', draw)
  draw()

  close = overlay(el('div', 'sheet-list picker', [
    el('h2', 'sheet-title', 'Adicionar exercício'),
    el('div', 'search-wrap', [icon('search', 18), search]),
    results,
  ]))
  setTimeout(() => search.focus(), 60)
}

/* --------------------------- Configuração do item --------------------------- */

function rangeRow(
  label: string,
  from: number,
  to: number,
  onChange: (from: number, to: number) => void,
): HTMLElement {
  const a = el('input', 'range-input') as HTMLInputElement
  const b = el('input', 'range-input') as HTMLInputElement
  for (const [inp, v] of [[a, from], [b, to]] as [HTMLInputElement, number][]) {
    inp.type = 'number'
    inp.inputMode = 'numeric'
    inp.min = '1'
    inp.value = String(v)
  }
  const emit = () => {
    const lo = Math.max(1, parseInt(a.value, 10) || 1)
    const hi = Math.max(lo, parseInt(b.value, 10) || lo)
    b.value = String(hi)
    onChange(lo, hi)
  }
  a.addEventListener('change', emit)
  b.addEventListener('change', emit)
  return el('div', 'cfg-row', [
    el('span', 'cfg-label', label),
    el('div', 'range', [a, el('span', 'range-sep', '–'), b]),
  ])
}

function numberRow(
  label: string,
  value: number,
  min: number,
  format: (v: number) => string,
  step: number,
  onChange: (v: number) => void,
): HTMLElement {
  const out = el('span', 'cfg-value', format(value))
  let v = value
  const set = (next: number) => {
    v = Math.max(min, next)
    out.textContent = format(v)
    onChange(v)
  }
  return el('div', 'cfg-row', [
    el('span', 'cfg-label', label),
    el('div', 'cfg-stepper', [
      button('cfg-btn', icon('minus', 18), () => set(v - step)),
      out,
      button('cfg-btn', icon('plus', 18), () => set(v + step)),
    ]),
  ])
}

/** Configura um item do treino. `onDelete` ausente = item novo. */
export function openItemSheet(
  base: WorkoutItem,
  onSave: (item: WorkoutItem) => void,
  onDelete?: () => void,
): void {
  const item: WorkoutItem = { ...base }
  let close: () => void

  const body = el('div', 'cfg-body')

  const draw = () => {
    body.innerHTML = ''

    const kinds = el('div', 'segmented')
    for (const [k, label] of [['carga', 'Carga × reps'], ['tempo', 'Tempo']] as [ItemKind, string][]) {
      kinds.append(button(`chip chip-wide${item.kind === k ? ' on' : ''}`, label, () => {
        item.kind = k
        if (k === 'carga') item.reps ??= [8, 12]
        else item.minutes ??= [20, 30]
        draw()
      }))
    }
    body.append(el('div', 'cfg-row', [el('span', 'cfg-label', 'Tipo'), kinds]))

    body.append(numberRow('Séries', item.sets, 1, (v) => String(v), 1, (v) => (item.sets = v)))

    if (item.kind === 'carga') {
      const [lo, hi] = item.reps ?? [8, 12]
      body.append(rangeRow('Reps', lo, hi, (a, b) => (item.reps = [a, b])))

      const rirOn = item.rir !== undefined
      const toggle = button(`toggle${rirOn ? ' on' : ''}`, el('span', 'knob'), () => {
        item.rir = item.rir === undefined ? '2' : undefined
        draw()
      })
      body.append(el('div', 'cfg-row', [el('span', 'cfg-label', 'RIR'), toggle]))
      if (rirOn) {
        const seg = el('div', 'segmented')
        for (const v of ['0', '1', '1–2', '2', '2–3', '3']) {
          seg.append(button(`chip${item.rir === v ? ' on' : ''}`, v, () => {
            item.rir = v
            seg.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c.textContent === v))
          }))
        }
        body.append(el('div', 'cfg-row cfg-row-wide', [seg]))
      }
    } else {
      const [lo, hi] = item.minutes ?? [20, 30]
      body.append(rangeRow('Minutos', lo, hi, (a, b) => (item.minutes = [a, b])))
    }

    body.append(numberRow('Descanso', item.rest, 0, (v) => (v === 0 ? 'sem' : fmtRest(v)), 15,
      (v) => (item.rest = v)))
  }

  draw()

  const actions: (HTMLElement | null)[] = [
    button('primary-btn', 'Salvar', () => {
      close()
      onSave(item)
    }),
    onDelete
      ? button('ghost-btn danger', [icon('trash', 17), el('span', '', 'Remover do treino')], () => {
          if (!confirm(`Remover ${exerciseName(item.exercise)} do treino?`)) return
          close()
          onDelete()
        })
      : null,
  ]

  close = overlay(el('div', 'sheet-list', [
    el('h2', 'sheet-title', exerciseName(item.exercise)),
    body,
    ...actions,
  ]))
}

/** Item novo com padrões sensatos a partir do tipo do exercício. */
export function defaultItem(exerciseId: string): WorkoutItem {
  const kind = exerciseKind(exerciseId)
  return kind === 'tempo'
    ? { exercise: exerciseId, kind, sets: 1, minutes: [20, 30], rest: 0 }
    : { exercise: exerciseId, kind, sets: 3, reps: [8, 12], rir: '2', rest: 120 }
}
