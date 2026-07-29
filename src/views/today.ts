import { DAY_KEYS, DAY_NAMES, exerciseName, exerciseNotes, program } from '../program'
import { formatDayMonth, todayKey, weekdayOf } from '../dates'
import { addSet, getDayLog, loadLogs, removeSet, setCardio, setNote, setTemplate } from '../storage'
import { computeProgression, type ProgressionState, type Session } from '../progression'
import type { CardioDay, DayKey, GymDay, GymItem, SetEntry } from '../types'
import { button, el, fmtRest, fmtWeight, holdButton, icon, overlay } from '../ui'
import { startRest } from '../timer'
import { unlockAudio } from '../audio'
import { keepAwake } from '../wakelock'

interface Draft {
  w: number
  r: number
  rir?: number
}

// Estado efêmero da tela (não persiste): valores dos steppers e cards abertos à força.
const drafts = new Map<string, Draft>()
const expandOverride = new Map<string, boolean>()
const notesOpen = new Set<string>()
let cardioEditing = false

function fmtSets(sets: SetEntry[]): string {
  return sets.map((s) => `${fmtWeight(s.w)}kg×${s.r}`).join(', ')
}

function getDraft(ex: string, item: GymItem, todaySets: SetEntry[], last?: Session): Draft {
  const existing = drafts.get(ex)
  if (existing) return existing
  const next = todaySets.length
  const draft: Draft = {
    w: todaySets.at(-1)?.w ?? last?.sets[next]?.w ?? last?.sets[0]?.w ?? 0,
    r: last?.sets[next]?.r ?? last?.sets.at(-1)?.r ?? item.reps[0],
  }
  drafts.set(ex, draft)
  return draft
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

function progressionBanner(item: GymItem, prog: ProgressionState): HTMLElement[] {
  const out: HTMLElement[] = []
  if (prog.kind === 'primeira-vez') {
    out.push(
      el('div', 'banner banner-first',
        `Primeira vez — anote as cargas. Alvo: ${item.sets} × ${item.reps[0]}–${item.reps[1]}.`),
    )
    return out
  }
  out.push(
    el('div', 'last-session', [
      icon('history', 15),
      el('span', 'last-date', `${formatDayMonth(prog.last.date)}`),
      el('span', 'last-sets', fmtSets(prog.last.sets)),
    ]),
  )
  if (prog.kind === 'subir-carga') {
    out.push(el('div', 'banner banner-up', [
      icon('trend', 17),
      el('span', '', `Suba a carga (menor incremento) e volte para ${item.reps[0]} reps.`),
    ]))
  } else {
    out.push(el('div', 'banner banner-meta',
      `Meta: repetir as cargas e somar reps até ${item.sets}×${item.reps[1]}.`))
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

function gymCard(
  date: string,
  template: DayKey,
  item: GymItem,
  rerender: () => void,
): HTMLElement {
  const ex = item.exercise
  const logs = loadLogs()
  const todaySets = logs[date]?.sets[ex] ?? []
  const done = todaySets.length >= item.sets
  const prog = computeProgression(ex, item, logs, date)
  const last = prog.kind === 'primeira-vez' ? undefined : prog.last
  const collapsed = done && expandOverride.get(ex) !== true

  const card = el('section', `block${done ? ' block-done' : ''}`)

  const pips = el('div', 'pips')
  for (let i = 0; i < item.sets; i++) {
    pips.append(el('div', `pip${i < todaySets.length ? ' on' : ''}`))
  }

  const scheme = `${item.sets} × ${item.reps[0]}–${item.reps[1]} · RIR ${item.rir} · ${fmtRest(item.rest)}`
  const header = el('header', 'item-head', [
    el('div', 'item-main', [
      el('h2', 'item-name', exerciseName(ex)),
      el('div', 'meta-line', scheme),
    ]),
    done ? el('div', 'done-check', icon('check', 22)) : pips,
  ])
  header.addEventListener('click', () => {
    if (!done) return
    expandOverride.set(ex, collapsed)
    rerender()
  })
  card.append(header)

  if (collapsed) {
    card.append(el('div', 'item-summary', fmtSets(todaySets)))
    return card
  }

  card.append(...progressionBanner(item, prog))

  const notes = exerciseNotes(ex)
  if (notes) {
    const toggle = button('notes-toggle', [icon('info', 15), el('span', '', 'Observações')], () => {
      if (notesOpen.has(ex)) notesOpen.delete(ex)
      else notesOpen.add(ex)
      rerender()
    })
    card.append(toggle)
    if (notesOpen.has(ex)) card.append(el('div', 'notes-body', notes))
  }

  if (todaySets.length > 0) {
    const list = el('div', 'set-log')
    todaySets.forEach((s, i) => {
      const line = el('div', 'set-row', [
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
      ])
      list.append(line)
    })
    card.append(list)
  }

  // Linha de registro da próxima série
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

  const segmented = el('div', 'segmented')
  for (let v = 0; v <= 4; v++) {
    const chip = button(`chip${draft.rir === v ? ' on' : ''}`, String(v), () => {
      draft.rir = draft.rir === v ? undefined : v
      segmented.querySelectorAll('.chip').forEach((c, i) => c.classList.toggle('on', draft.rir === i))
    })
    segmented.append(chip)
  }
  const chips = el('div', 'segmented-row', [el('span', 'seg-label', 'RIR'), segmented])

  const confirmBtn = button('log-btn', [icon('check', 21), el('span', '', 'Registrar série')], () => {
    unlockAudio()
    keepAwake()
    const entry: SetEntry = { w: draft.w, r: draft.r }
    if (draft.rir !== undefined) entry.rir = draft.rir
    addSet(date, template, ex, entry)
    startRest(item.rest, exerciseName(ex))
    rerender()
  })

  card.append(el('div', 'entry', [wGroup, rGroup]), chips, confirmBtn)
  return card
}

function renderGym(
  root: HTMLElement,
  date: string,
  template: DayKey,
  day: GymDay,
  rerender: () => void,
): HTMLElement {
  const logs = loadLogs()
  const daySets = logs[date]?.sets ?? {}
  const totalPlanned = day.items.reduce((a, i) => a + i.sets, 0)
  const totalDone = day.items.reduce(
    (a, i) => a + Math.min(daySets[i.exercise]?.length ?? 0, i.sets),
    0,
  )

  const bar = el('div', 'progress', [el('div', 'progress-fill')])
  ;(bar.firstElementChild as HTMLElement).style.width =
    `${totalPlanned ? Math.round((totalDone / totalPlanned) * 100) : 0}%`
  const progress = el('div', 'progress-wrap', [
    bar,
    el('div', 'progress-text', [el('b', '', String(totalDone)), `/${totalPlanned} séries`]),
  ])

  for (const item of day.items) {
    root.append(gymCard(date, template, item, rerender))
  }
  return progress
}

function renderCardio(
  root: HTMLElement,
  date: string,
  template: DayKey,
  day: CardioDay,
  rerender: () => void,
): void {
  const log = getDayLog(date)
  const [minLo, minHi] = day.cardio.minutes

  root.append(
    el('section', 'block', [
      el('span', 'eyebrow', 'Meta do dia'),
      el('div', 'hero-goal', [
        el('span', 'big', `${minLo}–${minHi}`),
        el('span', 'cap', 'min'),
      ]),
      el('p', 'guidance', day.cardio.guidance),
    ]),
  )

  if (log?.cardio && !cardioEditing) {
    const c = log.cardio
    const parts = [`${c.minutes} min`]
    if (c.km) parts.push(`${fmtWeight(c.km)} km`)
    if (c.local) parts.push(c.local)
    root.append(
      el('section', 'block block-done', [
        el('div', 'cardio-done', [icon('check', 19), el('span', '', parts.join(' · '))]),
        button('ghost-btn', 'Editar', () => {
          cardioEditing = true
          rerender()
        }),
      ]),
    )
    return
  }

  const draft = {
    minutes: log?.cardio?.minutes ?? 35,
    km: log?.cardio?.km !== undefined ? String(log.cardio.km) : '',
    local: log?.cardio?.local ?? 'esteira',
  }

  const minInput = numberInput(draft.minutes, false, (v) => (draft.minutes = v))
  const minGroup = stepper('min', minInput,
    () => {
      draft.minutes = Math.max(0, draft.minutes - 1)
      minInput.value = String(draft.minutes)
    },
    () => {
      draft.minutes += 1
      minInput.value = String(draft.minutes)
    },
  )

  const kmInput = el('input', 'text-input') as HTMLInputElement
  kmInput.type = 'text'
  kmInput.inputMode = 'decimal'
  kmInput.placeholder = 'km (opcional)'
  kmInput.value = draft.km
  kmInput.addEventListener('input', () => (draft.km = kmInput.value))

  const localSeg = el('div', 'segmented')
  for (const loc of ['esteira', 'rua']) {
    const chip = button(`chip${draft.local === loc ? ' on' : ''}`, loc, () => {
      draft.local = loc
      localSeg.querySelectorAll('.chip').forEach((c) =>
        c.classList.toggle('on', c.textContent === draft.local),
      )
    })
    localSeg.append(chip)
  }

  const save = button('primary-btn', [icon('run', 19), el('span', '', 'Registrar corrida')], () => {
    const km = parseFloat(draft.km.replace(',', '.'))
    setCardio(date, template, {
      minutes: draft.minutes,
      ...(Number.isNaN(km) || draft.km.trim() === '' ? {} : { km }),
      local: draft.local,
    })
    cardioEditing = false
    rerender()
  })

  root.append(el('section', 'block', [
    el('div', 'entry', [minGroup]),
    kmInput,
    el('div', 'segmented-row', [localSeg]),
    save,
  ]))
}

function openTemplateSheet(date: string, current: DayKey, rerender: () => void): void {
  const list = el('div', 'sheet-list', el('h2', 'sheet-title', 'Fazer outro treino'))
  let close: () => void
  for (const key of DAY_KEYS) {
    const day = program.week[key]
    const b = button(`sheet-item${key === current ? ' current' : ''}`,
      el('span', '', `${DAY_NAMES[key]} — ${day.title}`), () => {
        setTemplate(date, key)
        close()
        rerender()
      })
    if (key === current) b.append(icon('check', 19))
    list.append(b)
  }
  close = overlay(list)
}

function openNoteSheet(date: string, template: DayKey, rerender: () => void): void {
  const existing = getDayLog(date)?.note ?? ''
  const ta = el('textarea', 'note-input') as HTMLTextAreaElement
  ta.value = existing
  ta.placeholder = 'Como foi o treino? Dores, ajustes, observações…'
  ta.rows = 4
  let close: () => void
  const save = button('primary-btn', 'Salvar nota', () => {
    setNote(date, template, ta.value)
    close()
    rerender()
  })
  close = overlay(el('div', 'sheet-list', [el('h2', 'sheet-title', 'Nota do dia'), ta, save]))
}

export function renderToday(root: HTMLElement): void {
  const rerender = () => renderToday(root)
  const date = todayKey()
  const realDay = weekdayOf(date)
  const log = getDayLog(date)
  const template = log?.template ?? realDay
  const day = program.week[template]

  root.innerHTML = ''

  const switched = template !== realDay
  const header = el('header', 'app-header', [
    el('div', 'header-row', [
      el('div', 'header-titles', [
        el('span', 'eyebrow', DAY_NAMES[realDay]),
        el('h1', '', day.title),
        switched ? el('div', 'header-badge', `treino de ${DAY_NAMES[template].toLowerCase()}`) : null,
      ]),
      button('icon-btn', icon('swap', 20), () => openTemplateSheet(date, template, rerender)),
    ]),
  ])
  root.append(header)

  if (day.type === 'cardio') renderCardio(root, date, template, day, rerender)
  else header.append(renderGym(root, date, template, day, rerender))

  const note = getDayLog(date)?.note
  root.append(
    el('div', 'day-note-wrap', [
      note ? el('div', 'day-note', [icon('note', 16), el('span', '', note)]) : null,
      button('ghost-btn', [icon('note', 17), el('span', '', note ? 'Editar nota do dia' : 'Nota do dia')], () =>
        openNoteSheet(date, template, rerender),
      ),
    ]),
  )
}
