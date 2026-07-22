/** Cria um elemento com classe e conteúdo — o canivete do app inteiro. */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = '',
  content?: string | Node | (string | Node | null)[],
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

export function button(
  className: string,
  label: string | Node,
  onClick: (ev: MouseEvent) => void,
): HTMLButtonElement {
  const b = el('button', className, label)
  b.type = 'button'
  b.addEventListener('click', onClick)
  return b
}

/** Botão de stepper com repetição ao segurar. */
export function holdButton(className: string, label: string, step: () => void): HTMLButtonElement {
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
  const sheet = el('div', 'sheet', content)
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
