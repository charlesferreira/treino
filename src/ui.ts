export type Content = string | Node | (string | Node | null)[]

/** Cria um elemento com classe e conteúdo — o canivete do app inteiro. */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = '',
  content?: Content,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag)
  if (className) node.className = className
  if (content !== undefined) {
    const items = Array.isArray(content) ? content : [content]
    for (const item of items) {
      if (item === null) continue
      node.append(item)
    }
  }
  return node
}

/** Ícones em linha (traço, 24×24) — nada de emoji na estrutura da interface. */
const ICONS = {
  dumbbell: 'M6.5 6.5l11 11M21 21l-1-1M3 3l1 1M18 22l4-4M2 6l4-4M3 10l7-7M14 21l7-7',
  calendar: 'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z',
  swap: 'M8 3L4 7l4 4M4 7h16M16 21l4-4-4-4M20 17H4',
  check: 'M20 6L9 17l-5-5',
  plus: 'M12 5v14M5 12h14',
  minus: 'M5 12h14',
  close: 'M18 6L6 18M6 6l12 12',
  chevron: 'M9 18l6-6-6-6',
  info: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-4M12 8h.01',
  note: 'M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z',
  trend: 'M16 7h6v6M22 7l-8.5 8.5-5-5L2 17',
  alert: 'M10.3 4l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.7-3l-8-14a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01',
  history: 'M3 12a9 9 0 1 0 9-9 9.8 9.8 0 0 0-6.7 2.7L3 8M3 3v5h5',
  clock: 'M10 2h4M12 14v-4M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16z',
  run: 'M22 12h-4l-3 9L9 3l-3 9H2',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12',
  back: 'M19 12H5M12 19l-7-7 7-7',
  caret: 'M6 9l6 6 6-6',
  up: 'M18 15l-6-6-6 6',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  trash: 'M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6',
  copy: 'M8 4h10a2 2 0 0 1 2 2v10M16 8H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2z',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.3-4.3',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
} as const

export type IconName = keyof typeof ICONS

export function icon(name: IconName, size = 20): SVGSVGElement {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('viewBox', '0 0 24 24')
  svg.setAttribute('width', String(size))
  svg.setAttribute('height', String(size))
  svg.setAttribute('fill', 'none')
  svg.setAttribute('stroke', 'currentColor')
  svg.setAttribute('stroke-width', '2')
  svg.setAttribute('stroke-linecap', 'round')
  svg.setAttribute('stroke-linejoin', 'round')
  svg.setAttribute('aria-hidden', 'true')
  svg.classList.add('icon')
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  path.setAttribute('d', ICONS[name])
  svg.append(path)
  return svg
}

export function button(
  className: string,
  label: Content,
  onClick: (ev: MouseEvent) => void,
): HTMLButtonElement {
  const b = el('button', className, label)
  b.type = 'button'
  b.addEventListener('click', onClick)
  return b
}

/** Botão de stepper com repetição ao segurar. */
export function holdButton(className: string, label: Content, step: () => void): HTMLButtonElement {
  const b = el('button', className, label)
  b.type = 'button'
  let timeout = 0
  let interval = 0
  const stop = () => {
    clearTimeout(timeout)
    clearInterval(interval)
  }
  b.addEventListener('pointerdown', (ev) => {
    ev.preventDefault()
    step()
    timeout = window.setTimeout(() => {
      interval = window.setInterval(step, 110)
    }, 450)
  })
  for (const evName of ['pointerup', 'pointerleave', 'pointercancel'] as const) {
    b.addEventListener(evName, stop)
  }
  return b
}

/** Overlay modal simples; retorna função de fechar. */
export function overlay(content: HTMLElement): () => void {
  const wrap = el('div', 'overlay')
  const sheet = el('div', 'sheet', [el('div', 'sheet-grabber'), content])
  wrap.append(sheet)
  wrap.addEventListener('click', (ev) => {
    if (ev.target === wrap) close()
  })
  const close = () => wrap.remove()
  document.body.append(wrap)
  return close
}

export function fmtRest(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m === 0) return `${s}s`
  if (s === 0) return `${m}min`
  return `${m}min${String(s).padStart(2, '0')}`
}

export function fmtClock(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function fmtWeight(w: number): string {
  return Number.isInteger(w) ? String(w) : String(w).replace('.', ',')
}
