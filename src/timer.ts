import { beep, vibrate } from './audio'
import { button, el, fmtClock, icon } from './ui'

const RADIUS = 56
const CIRC = 2 * Math.PI * RADIUS

let endAt = 0
let total = 1
let interval = 0
let box: HTMLElement | null = null
let timeEl: HTMLElement | null = null
let labelEl: HTMLElement | null = null
let ringEl: SVGCircleElement | null = null
let doneTimeout = 0

function circle(className: string): SVGCircleElement {
  const c = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
  c.setAttribute('cx', '66')
  c.setAttribute('cy', '66')
  c.setAttribute('r', String(RADIUS))
  c.setAttribute('class', className)
  return c
}

function ensureBox(): void {
  if (box) return
  labelEl = el('span', '')
  timeEl = el('div', 'timer-time')
  ringEl = circle('ring-value')
  ringEl.style.strokeDasharray = String(CIRC)

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('viewBox', '0 0 132 132')
  svg.append(circle('ring-track'), ringEl)

  const minus = button('timer-btn', '−15s', () => adjust(-15))
  const plus = button('timer-btn', '+15s', () => adjust(15))
  const skip = button('timer-btn timer-skip', 'Pular', hide)

  box = el('div', 'timer', [
    el('div', 'timer-head', [icon('clock', 13), labelEl]),
    el('div', 'timer-ring', [svg, timeEl]),
    el('div', 'timer-actions', [minus, plus, skip]),
  ])
  document.body.append(box)
}

function tick(): void {
  const left = Math.max(0, Math.round((endAt - Date.now()) / 1000))
  if (timeEl) timeEl.textContent = fmtClock(left)
  // O anel esvazia conforme o descanso corre.
  if (ringEl) ringEl.style.strokeDashoffset = String(CIRC * (1 - Math.min(1, left / total)))
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
  if (ringEl) ringEl.style.strokeDashoffset = '0'
  doneTimeout = window.setTimeout(hide, 5000)
}

function adjust(seconds: number): void {
  if (!interval) return
  endAt += seconds * 1000
  if (endAt <= Date.now()) endAt = Date.now()
  total = Math.max(total, Math.ceil((endAt - Date.now()) / 1000))
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
  ringEl = null
}

/** Inicia o descanso. Conta por diferença de relógio — sobrevive à tela apagada. */
export function startRest(seconds: number, label: string): void {
  hide()
  ensureBox()
  box?.classList.remove('timer-done')
  if (labelEl) labelEl.textContent = `Descanso · ${label}`
  total = Math.max(1, seconds)
  endAt = Date.now() + seconds * 1000
  tick()
  interval = window.setInterval(tick, 250)
}
