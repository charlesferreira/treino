import { beep, vibrate } from './audio'
import { button, el, fmtClock } from './ui'

let endAt = 0
let interval = 0
let box: HTMLElement | null = null
let timeEl: HTMLElement | null = null
let labelEl: HTMLElement | null = null
let doneTimeout = 0

function ensureBox(): void {
  if (box) return
  labelEl = el('div', 'timer-label')
  timeEl = el('div', 'timer-time')
  const minus = button('timer-btn', '−15s', () => adjust(-15))
  const plus = button('timer-btn', '+15s', () => adjust(15))
  const skip = button('timer-btn timer-skip', 'Pular', hide)
  box = el('div', 'timer', [
    labelEl,
    timeEl,
    el('div', 'timer-actions', [minus, plus, skip]),
  ])
  document.body.append(box)
}

function tick(): void {
  const left = Math.max(0, Math.round((endAt - Date.now()) / 1000))
  if (timeEl) timeEl.textContent = fmtClock(left)
  if (left <= 0) finish()
}

function finish(): void {
  clearInterval(interval)
  interval = 0
  beep()
  vibrate([200, 100, 200, 100, 400])
  box?.classList.add('timer-done')
  if (labelEl) labelEl.textContent = 'Descanso acabou — bora!'
  if (timeEl) timeEl.textContent = '0:00'
  doneTimeout = window.setTimeout(hide, 5000)
}

function adjust(seconds: number): void {
  if (!interval) return
  endAt += seconds * 1000
  if (endAt <= Date.now()) endAt = Date.now()
  tick()
}

export function hide(): void {
  clearInterval(interval)
  clearTimeout(doneTimeout)
  interval = 0
  box?.remove()
  box = null
  timeEl = null
  labelEl = null
}

/** Inicia o descanso. Conta por diferença de relógio — sobrevive à tela apagada. */
export function startRest(seconds: number, label: string): void {
  hide()
  ensureBox()
  box?.classList.remove('timer-done')
  if (labelEl) labelEl.textContent = `Descanso — ${label}`
  endAt = Date.now() + seconds * 1000
  tick()
  interval = window.setInterval(tick, 250)
}
