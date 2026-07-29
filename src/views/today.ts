import { DAY_NAMES } from '../program'
import { formatDayMonth, formatDayMonthShort, todayKey, weekdayOf } from '../dates'
import {
  addSet,
  addTime,
  getDayLog,
  loadLogs,
  removeSet,
  removeTime,
  setNote,
  setTemplate,
} from '../storage'
import { computeProgression, type ProgressionState, type Session } from '../progression'
import { exerciseName, exerciseNotes, findExercise } from '../exercises'
import { loadPlan, nextInSequence, suggestWorkout } from '../plan'
import type { SetEntry, TimeEntry, Workout, WorkoutItem } from '../types'
import { button, el, fmtRest, fmtWeight, holdButton, icon, overlay } from '../ui'
import { appHeader, avatarButton } from './chrome'
import { startRest } from '../timer'
import { unlockAudio } from '../audio'
import { keepAwake } from '../wakelock'

interface Draft {
  w: number
  r: number
  rir?: number
}

// Estado efêmero da tela (não persiste): valores dos steppers e blocos abertos à força.
const drafts = new Map<string, Draft>()
const timeDrafts = new Map<string, { min: number; km: string; local: string }>()
const expandOverride = new Map<string, boolean>()
const notesOpen = new Set<string>()

function fmtSets(sets: SetEntry[]): string {
  return sets.map((s) => `${fmtWeight(s.w)}kg×${s.r}`).join(', ')
}

function fmtTimes(times: TimeEntry[]): string {
  return times
    .map((t) => [`${t.min} min`, t.km ? `${fmtWeight(t.km)} km` : '', t.local ?? '']
      .filter(Boolean)
      .join(' · '))
    .join(', ')
}

function numberInput(value: number, decimals: boolean, onChange: (v: number) => void): HTMLInputElement {
  const input = el('input', 'num') as HTMLInputElement
  input.type = 'number'
  input.inputMode = decimals ? 'decimal' : 'numeric'
  if (decimals) input.step = '0.5'
  input.min = '0'
  input.value = fmtWeight(value).replace(',', '.')
  input.addEventListener('change', () => {
    const v = parseFloat(input.value.replace(',', '.'))
    if (!Number.isNaN(v) && v >= 0) onChange(v)
    else input.value = String(value)
  })
  return input
}

/** Stepper: − [valor / unidade] + */
function stepper(
  unit: string,
  input: HTMLInputElement,
  dec: () => void,
  inc: () => void,
): HTMLElement {
  return el('div', 'field', [
    el('div', 'stepper', [
      holdButton('step-btn', icon('minus', 22), dec),
      el('div', 'step-value', [input, el('span', 'unit', unit)]),
      holdButton('step-btn', icon('plus', 22), inc),
    ]),
  ])
}

function itemHead(item: WorkoutItem, doneCount: number, meta: string): HTMLElement {
  const done = doneCount >= item.sets
  const pips = el('div', 'pips')
  for (let i = 0; i < item.sets; i++) {
    pips.append(el('div', `pip${i < doneCount ? ' on' : ''}`))
  }
  return el('header', 'item-head', [
    el('div', 'item-main', [
      el('h2', 'item-name', exerciseName(item.exercise)),
      el('div', 'meta-line', meta),
    ]),
    done ? el('div', 'done-check', icon('check', 22)) : pips,
  ])
}

function notesToggle(ex: string, rerender: () => void): HTMLElement[] {
  const notes = exerciseNotes(ex)
  if (!notes) return []
  const out: HTMLElement[] = [
    button('notes-toggle', [icon('info', 15), el('span', '', 'Observações')], () => {
      if (notesOpen.has(ex)) notesOpen.delete(ex)
      else notesOpen.add(ex)
      rerender()
    }),
  ]
  if (notesOpen.has(ex)) out.push(el('div', 'notes-body', notes))
  return out
}

/* ------------------------------ Item por carga ------------------------------ */

function getDraft(ex: string, item: WorkoutItem, todaySets: SetEntry[], last?: Session): Draft {
  const existing = drafts.get(ex)
  if (existing) return existing
  const next = todaySets.length
  const draft: Draft = {
    w: todaySets.at(-1)?.w ?? last?.sets[next]?.w ?? last?.sets[0]?.w ?? 0,
    r: last?.sets[next]?.r ?? last?.sets.at(-1)?.r ?? item.reps?.[0] ?? 10,
  }
  drafts.set(ex, draft)
  return draft
}

function progressionBanner(item: WorkoutItem, prog: ProgressionState): HTMLElement[] {
  const [lo, hi] = item.reps ?? [8, 12]
  const out: HTMLElement[] = []
  if (prog.kind === 'primeira-vez') {
    out.push(
      el('div', 'banner banner-first',
        `Primeira vez — anote as cargas. Alvo: ${item.sets} × ${lo}–${hi}.`),
    )
    return out
  }
  out.push(
    el('div', 'last-session', [
      icon('history', 15),
      el('span', 'last-date', formatDayMonth(prog.last.date)),
      el('span', 'last-sets', fmtSets(prog.last.sets)),
    ]),
  )
  if (prog.kind === 'subir-carga') {
    out.push(el('div', 'banner banner-up', [
      icon('trend', 17),
      el('span', '', `Suba a carga (menor incremento) e volte para ${lo} reps.`),
    ]))
  } else {
    out.push(el('div', 'banner banner-meta',
      `Meta: repetir as cargas e somar reps até ${item.sets}×${hi}.`))
  }
  if (prog.plateau) {
    out.push(el('div', 'banner banner-warn', [
      icon('alert', 17),
      el('span', '',
        '3 sessões sem progresso — cheque sono e proteína; se ok, troque a variação ou tire 10% e resuba.'),
    ]))
  }
  return out
}

function loadBlock(
  date: string,
  workout: string,
  item: WorkoutItem,
  rerender: () => void,
): HTMLElement {
  const ex = item.exercise
  const logs = loadLogs()
  const todaySets = logs[date]?.sets[ex] ?? []
  const done = todaySets.length >= item.sets
  const prog = computeProgression(ex, item, logs, date)
  const last = prog.kind === 'primeira-vez' ? undefined : prog.last
  const collapsed = done && expandOverride.get(ex) !== true
  const [lo, hi] = item.reps ?? [8, 12]

  const block = el('section', `block${done ? ' block-done' : ''}`)
  const meta = [
    `${item.sets} × ${lo}–${hi}`,
    item.rir ? `RIR ${item.rir}` : '',
    fmtRest(item.rest),
  ].filter(Boolean).join(' · ')

  const header = itemHead(item, todaySets.length, meta)
  header.addEventListener('click', () => {
    if (!done) return
    expandOverride.set(ex, collapsed)
    rerender()
  })
  block.append(header)

  if (collapsed) {
    block.append(el('div', 'item-summary', fmtSets(todaySets)))
    return block
  }

  block.append(...progressionBanner(item, prog))
  block.append(...notesToggle(ex, rerender))

  if (todaySets.length > 0) {
    const list = el('div', 'set-log')
    todaySets.forEach((s, i) => {
      list.append(el('div', 'set-row', [
        el('span', 'set-idx', String(i + 1)),
        el('span', 'set-val', [
          el('b', '', fmtWeight(s.w)),
          el('span', 'unit', ' kg'),
          el('span', 'mul', '×'),
          el('b', '', String(s.r)),
        ]),
        s.rir !== undefined ? el('span', 'set-rir', `RIR ${s.rir}`) : null,
        button('set-del', icon('close', 16), () => {
          if (confirm(`Apagar a ${i + 1}ª série de ${exerciseName(ex)}?`)) {
            removeSet(date, ex, i)
            rerender()
          }
        }),
      ]))
    })
    block.append(list)
  }

  const draft = getDraft(ex, item, todaySets, last)
  const wInput = numberInput(draft.w, true, (v) => (draft.w = v))
  const rInput = numberInput(draft.r, false, (v) => (draft.r = v))

  const wGroup = stepper('kg', wInput,
    () => {
      draft.w = Math.max(0, Math.round((draft.w - 1) * 2) / 2)
      wInput.value = String(draft.w)
    },
    () => {
      draft.w = Math.round((draft.w + 1) * 2) / 2
      wInput.value = String(draft.w)
    },
  )
  const rGroup = stepper('reps', rInput,
    () => {
      draft.r = Math.max(1, draft.r - 1)
      rInput.value = String(draft.r)
    },
    () => {
      draft.r = draft.r + 1
      rInput.value = String(draft.r)
    },
  )
  block.append(el('div', 'entry', [wGroup, rGroup]))

  if (item.rir) {
    const segmented = el('div', 'segmented')
    for (let v = 0; v <= 4; v++) {
      segmented.append(button(`chip${draft.rir === v ? ' on' : ''}`, String(v), () => {
        draft.rir = draft.rir === v ? undefined : v
        segmented.querySelectorAll('.chip').forEach((c, i) => c.classList.toggle('on', draft.rir === i))
      }))
    }
    block.append(el('div', 'segmented-row', [el('span', 'seg-label', 'RIR'), segmented]))
  }

  block.append(button('log-btn', [icon('check', 21), el('span', '', 'Registrar série')], () => {
    unlockAudio()
    keepAwake()
    const entry: SetEntry = { w: draft.w, r: draft.r }
    if (item.rir && draft.rir !== undefined) entry.rir = draft.rir
    addSet(date, workout, ex, entry)
    if (item.rest > 0) startRest(item.rest, exerciseName(ex))
    rerender()
  }))
  return block
}

/* ------------------------------- Item por tempo ------------------------------ */

function timeBlock(
  date: string,
  workout: string,
  item: WorkoutItem,
  rerender: () => void,
): HTMLElement {
  const ex = item.exercise
  const logs = loadLogs()
  const todayTimes = logs[date]?.times?.[ex] ?? []
  const done = todayTimes.length >= item.sets
  const collapsed = done && expandOverride.get(ex) !== true
  const [lo, hi] = item.minutes ?? [20, 30]
  const isCardio = findExercise(ex)?.group === 'cardio'

  const block = el('section', `block${done ? ' block-done' : ''}`)
  const meta = [
    item.sets > 1 ? `${item.sets} × ${lo}–${hi} min` : `${lo}–${hi} min`,
    item.rest > 0 ? fmtRest(item.rest) : '',
  ].filter(Boolean).join(' · ')

  const header = itemHead(item, todayTimes.length, meta)
  header.addEventListener('click', () => {
    if (!done) return
    expandOverride.set(ex, collapsed)
    rerender()
  })
  block.append(header)

  if (collapsed) {
    block.append(el('div', 'item-summary', fmtTimes(todayTimes)))
    return block
  }

  if (item.notes) block.append(el('p', 'guidance', item.notes))
  block.append(...notesToggle(ex, rerender))

  // Última vez que esse exercício foi feito
  const prev = Object.keys(logs)
    .filter((d) => d !== date && (logs[d].times?.[ex]?.length ?? 0) > 0)
    .sort()
    .at(-1)
  if (prev) {
    block.append(el('div', 'last-session', [
      icon('history', 15),
      el('span', 'last-date', formatDayMonth(prev)),
      el('span', 'last-sets', fmtTimes(logs[prev].times![ex])),
    ]))
  }

  if (todayTimes.length > 0) {
    const list = el('div', 'set-log')
    todayTimes.forEach((t, i) => {
      list.append(el('div', 'set-row', [
        el('span', 'set-idx', String(i + 1)),
        el('span', 'set-val', [
          el('b', '', String(t.min)),
          el('span', 'unit', ' min'),
          ...(t.km ? [el('span', 'mul', '·'), el('b', '', fmtWeight(t.km)), el('span', 'unit', ' km')] : []),
        ]),
        t.local ? el('span', 'set-rir', t.local) : null,
        button('set-del', icon('close', 16), () => {
          if (confirm(`Apagar esse registro de ${exerciseName(ex)}?`)) {
            removeTime(date, ex, i)
            rerender()
          }
        }),
      ]))
    })
    block.append(list)
  }

  const draft = timeDrafts.get(ex) ?? { min: lo, km: '', local: 'esteira' }
  timeDrafts.set(ex, draft)

  const minInput = numberInput(draft.min, false, (v) => (draft.min = v))
  block.append(el('div', 'entry', [
    stepper('min', minInput,
      () => {
        draft.min = Math.max(0, draft.min - 1)
        minInput.value = String(draft.min)
      },
      () => {
        draft.min += 1
        minInput.value = String(draft.min)
      },
    ),
  ]))

  if (isCardio) {
    const kmInput = el('input', 'text-input') as HTMLInputElement
    kmInput.type = 'text'
    kmInput.inputMode = 'decimal'
    kmInput.placeholder = 'km (opcional)'
    kmInput.value = draft.km
    kmInput.addEventListener('input', () => (draft.km = kmInput.value))
    block.append(kmInput)

    const seg = el('div', 'segmented')
    for (const loc of ['esteira', 'rua']) {
      seg.append(button(`chip chip-wide${draft.local === loc ? ' on' : ''}`, loc, () => {
        draft.local = loc
        seg.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c.textContent === draft.local))
      }))
    }
    block.append(el('div', 'segmented-row', [seg]))
  }

  block.append(button('log-btn', [icon('check', 21), el('span', '', 'Registrar')], () => {
    unlockAudio()
    const km = parseFloat(draft.km.replace(',', '.'))
    const entry: TimeEntry = { min: draft.min }
    if (isCardio) {
      if (!Number.isNaN(km) && draft.km.trim() !== '') entry.km = km
      entry.local = draft.local
    }
    addTime(date, workout, ex, entry)
    if (item.rest > 0) startRest(item.rest, exerciseName(ex))
    rerender()
  }))
  return block
}

/* ---------------------------------- Tela ------------------------------------ */

function openWorkoutSheet(date: string, current: string, rerender: () => void): void {
  const list = el('div', 'sheet-list', el('h2', 'sheet-title', 'Treino de hoje'))
  let close: () => void
  for (const w of loadPlan().workouts) {
    const b = button(`sheet-item${w.id === current ? ' current' : ''}`,
      el('span', '', w.name), () => {
        setTemplate(date, w.id)
        close()
        rerender()
      })
    if (w.id === current) b.append(icon('check', 19))
    list.append(b)
  }
  list.append(button('ghost-btn', [icon('list', 17), el('span', '', 'Gerenciar treinos')], () => {
    close()
    location.hash = '#treinos'
  }))
  close = overlay(list)
}

function openNoteSheet(date: string, workout: string, rerender: () => void): void {
  const existing = getDayLog(date)?.note ?? ''
  const ta = el('textarea', 'note-input') as HTMLTextAreaElement
  ta.value = existing
  ta.placeholder = 'Como foi o treino? Dores, ajustes, observações…'
  ta.rows = 4
  let close: () => void
  const save = button('primary-btn', 'Salvar nota', () => {
    setNote(date, workout, ta.value)
    close()
    rerender()
  })
  close = overlay(el('div', 'sheet-list', [el('h2', 'sheet-title', 'Nota do dia'), ta, save]))
}

function progressBar(date: string, workout: Workout): HTMLElement {
  const logs = loadLogs()
  const log = logs[date]
  const planned = workout.items.reduce((a, i) => a + i.sets, 0)
  const done = workout.items.reduce((a, i) => {
    const n = i.kind === 'tempo'
      ? (log?.times?.[i.exercise]?.length ?? 0)
      : (log?.sets[i.exercise]?.length ?? 0)
    return a + Math.min(n, i.sets)
  }, 0)

  const bar = el('div', 'progress', [el('div', 'progress-fill')])
  ;(bar.firstElementChild as HTMLElement).style.width =
    `${planned ? Math.round((done / planned) * 100) : 0}%`
  // "séries" só faz sentido quando há item de carga; num treino só de tempo, "feito".
  const unit = workout.items.some((i) => i.kind === 'carga')
    ? `/${planned} série${planned > 1 ? 's' : ''}`
    : `/${planned} feito${planned > 1 ? 's' : ''}`
  return el('div', 'progress-wrap', [
    bar,
    el('div', 'progress-text', [el('b', '', String(done)), unit]),
  ])
}

export function renderToday(root: HTMLElement): void {
  const rerender = () => renderToday(root)
  const date = todayKey()
  const logs = loadLogs()
  const workout = suggestWorkout(date, logs)

  root.innerHTML = ''

  const eyebrow = `${DAY_NAMES[weekdayOf(date)]} · ${formatDayMonthShort(date)}`

  if (!workout) {
    root.append(appHeader({ eyebrow, title: 'Sem treino', right: avatarButton() }))
    root.append(
      el('p', 'empty', 'Nenhum treino configurado ainda.'),
      button('primary-btn', [icon('plus', 19), el('span', '', 'Criar meu primeiro treino')], () => {
        location.hash = '#treinos'
      }),
    )
    return
  }

  const chosen = logs[date]?.template
  const next = nextInSequence(date, logs)
  const offSequence = chosen !== undefined && next !== undefined && chosen !== next.id

  root.append(appHeader({
    eyebrow,
    title: workout.name,
    onTitle: () => openWorkoutSheet(date, workout.id, rerender),
    badge: offSequence ? 'fora da sequência' : undefined,
    right: avatarButton(),
    below: progressBar(date, workout),
  }))

  if (workout.items.length === 0) {
    root.append(
      el('p', 'empty', 'Esse treino ainda não tem exercícios.'),
      button('primary-btn', [icon('plus', 19), el('span', '', 'Adicionar exercícios')], () => {
        location.hash = `#treino/${workout.id}`
      }),
    )
    return
  }

  for (const item of workout.items) {
    root.append(item.kind === 'tempo'
      ? timeBlock(date, workout.id, item, rerender)
      : loadBlock(date, workout.id, item, rerender))
  }

  const note = getDayLog(date)?.note
  root.append(
    el('div', 'day-note-wrap', [
      note ? el('div', 'day-note', [icon('note', 16), el('span', '', note)]) : null,
      button('ghost-btn', [icon('note', 17), el('span', '', note ? 'Editar nota do dia' : 'Nota do dia')], () =>
        openNoteSheet(date, workout.id, rerender),
      ),
    ]),
  )
}
