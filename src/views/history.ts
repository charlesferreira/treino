import { dayNumber, monthLabel, weekdayShort } from '../dates'
import { exerciseName } from '../exercises'
import { findWorkout } from '../plan'
import { loadLogs } from '../storage'
import type { DayLog, SetEntry, TimeEntry } from '../types'
import { el, fmtWeight, icon } from '../ui'
import { appHeader, avatarButton } from './chrome'

const openDays = new Set<string>()

function volume(log: DayLog): number {
  let total = 0
  for (const sets of Object.values(log.sets)) {
    for (const s of sets) total += s.w * s.r
  }
  return total
}

function setCount(log: DayLog): number {
  return Object.values(log.sets).reduce((a, s) => a + s.length, 0)
}

function totalMinutes(log: DayLog): number {
  return Object.values(log.times ?? {}).reduce(
    (a, entries) => a + entries.reduce((b, t) => b + t.min, 0),
    0,
  )
}

function fmtSets(sets: SetEntry[]): string {
  return sets
    .map((s) => `${fmtWeight(s.w)}kg×${s.r}${s.rir !== undefined ? ` @${s.rir}` : ''}`)
    .join(', ')
}

function fmtTimes(times: TimeEntry[]): string {
  return times
    .map((t) => [`${t.min} min`, t.km ? `${fmtWeight(t.km)} km` : '', t.local ?? '']
      .filter(Boolean)
      .join(' · '))
    .join(', ')
}

function detail(log: DayLog): HTMLElement {
  const box = el('div', 'day-detail')
  for (const [ex, sets] of Object.entries(log.sets)) {
    box.append(el('div', 'detail-row', [
      el('div', 'detail-ex', exerciseName(ex)),
      el('div', 'detail-sets', fmtSets(sets)),
    ]))
  }
  for (const [ex, times] of Object.entries(log.times ?? {})) {
    box.append(el('div', 'detail-row', [
      el('div', 'detail-ex', exerciseName(ex)),
      el('div', 'detail-sets', fmtTimes(times)),
    ]))
  }
  if (log.note) box.append(el('div', 'detail-note', [icon('note', 15), el('span', '', log.note)]))
  return box
}

export function renderHistory(root: HTMLElement): void {
  const rerender = () => renderHistory(root)
  root.innerHTML = ''
  root.append(appHeader({ title: 'Histórico', right: avatarButton() }))

  const logs = loadLogs()
  const dates = Object.keys(logs).sort().reverse()

  if (dates.length === 0) {
    root.append(el('p', 'empty', 'Nenhum treino registrado ainda. Bora começar.'))
    return
  }

  let lastMonth = ''
  for (const date of dates) {
    const month = date.slice(0, 7)
    if (month !== lastMonth) {
      root.append(el('div', 'month-label', monthLabel(date)))
      lastMonth = month
    }

    const log = logs[date]
    // Treino apagado depois: o dia continua no histórico, só perde o nome.
    const title = findWorkout(log.template)?.name ?? 'Treino removido'
    const n = setCount(log)
    const vol = volume(log)
    const min = totalMinutes(log)
    const open = openDays.has(date)

    const summary = el('div', 'history-summary')
    if (n > 0) {
      summary.append(
        el('span', '', [el('b', '', String(n)), ' séries']),
        el('span', '', [el('b', '', Math.round(vol).toLocaleString('pt-BR')), ' kg']),
      )
    }
    if (min > 0) summary.append(el('span', '', [el('b', '', String(min)), ' min']))
    if (n === 0 && min === 0) summary.append(el('span', '', 'sem registros'))

    const card = el('section', 'history-row', [
      el('div', 'history-line', [
        el('div', 'date-col', [
          el('span', 'd', dayNumber(date)),
          el('span', 'w', weekdayShort(date)),
        ]),
        el('div', 'history-main', [el('div', 'history-title', title), summary]),
        el('div', `history-chevron${open ? ' open' : ''}`, icon('chevron', 18)),
      ]),
    ])
    card.addEventListener('click', () => {
      if (open) openDays.delete(date)
      else openDays.add(date)
      rerender()
    })
    if (open) card.append(detail(log))
    root.append(card)
  }
}
