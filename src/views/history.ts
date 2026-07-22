import { formatShort } from '../dates'
import { exerciseName, program } from '../program'
import { loadLogs } from '../storage'
import type { DayLog, SetEntry } from '../types'
import { el, fmtWeight } from '../ui'

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

function fmtSets(sets: SetEntry[]): string {
  return sets
    .map((s) => `${fmtWeight(s.w)}kg×${s.r}${s.rir !== undefined ? ` @${s.rir}` : ''}`)
    .join(', ')
}

function detail(log: DayLog): HTMLElement {
  const box = el('div', 'day-detail')
  for (const [ex, sets] of Object.entries(log.sets)) {
    box.append(
      el('div', 'detail-row', [
        el('div', 'detail-ex', exerciseName(ex)),
        el('div', 'detail-sets', fmtSets(sets)),
      ]),
    )
  }
  if (log.cardio) {
    const parts = [`${log.cardio.minutes} min`]
    if (log.cardio.km) parts.push(`${fmtWeight(log.cardio.km)} km`)
    if (log.cardio.local) parts.push(log.cardio.local)
    box.append(el('div', 'detail-row', `🏃 ${parts.join(' · ')}`))
  }
  if (log.note) box.append(el('div', 'detail-note', `📝 ${log.note}`))
  return box
}

export function renderHistory(root: HTMLElement): void {
  const rerender = () => renderHistory(root)
  root.innerHTML = ''
  root.append(el('header', 'app-header', el('h1', '', 'Histórico')))

  const logs = loadLogs()
  const dates = Object.keys(logs).sort().reverse()

  if (dates.length === 0) {
    root.append(el('p', 'empty', 'Nenhum treino registrado ainda. Bora começar? 💪'))
    return
  }

  for (const date of dates) {
    const log = logs[date]
    const title = program.week[log.template]?.title ?? log.template
    const n = setCount(log)
    const vol = volume(log)
    const summary: string[] = []
    if (n > 0) summary.push(`${n} séries · ${vol.toLocaleString('pt-BR')} kg`)
    if (log.cardio) summary.push(`${log.cardio.minutes} min de corrida`)
    if (summary.length === 0) summary.push('sem registros')

    const card = el('section', 'card history-card', [
      el('div', 'history-line', [
        el('div', '', [
          el('div', 'history-date', `${formatShort(date)} — ${title}`),
          el('div', 'history-summary', summary.join(' · ')),
        ]),
        el('div', 'history-chevron', openDays.has(date) ? '▾' : '▸'),
      ]),
    ])
    card.addEventListener('click', () => {
      if (openDays.has(date)) openDays.delete(date)
      else openDays.add(date)
      rerender()
    })
    if (openDays.has(date)) card.append(detail(log))
    root.append(card)
  }
}
