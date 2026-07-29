import { initials, loadProfile } from '../plan'
import { button, el, icon, type Content } from '../ui'

/** Bolinha com as iniciais — atalho para o perfil. */
export function avatarButton(): HTMLButtonElement {
  const name = loadProfile().name
  const ini = initials(name)
  return button(
    'avatar',
    ini ? el('span', '', ini) : icon('user', 20),
    () => {
      location.hash = '#perfil'
    },
  )
}

export interface HeaderOptions {
  eyebrow?: string
  title: string
  /** Torna o título tocável (com a setinha) — usado para trocar de treino. */
  onTitle?: () => void
  badge?: string
  back?: string
  right?: Node | null
  below?: Node | null
}

export function appHeader(opts: HeaderOptions): HTMLElement {
  const titleContent: Content = opts.onTitle
    ? [el('span', '', opts.title), icon('caret', 22)]
    : opts.title

  const heading = opts.onTitle
    ? button('title-btn', titleContent, opts.onTitle)
    : el('h1', '', opts.title)

  const hasBack = opts.back !== undefined
  const titles = el('div', 'header-titles', [
    !hasBack && opts.eyebrow ? el('span', 'eyebrow', opts.eyebrow) : null,
    heading,
    opts.badge ? el('div', 'header-badge', opts.badge) : null,
  ])

  const left = hasBack
    ? el('div', 'header-back-row', [
        button('icon-btn', icon('back', 20), () => {
          location.hash = opts.back as string
        }),
        titles,
      ])
    : titles

  return el('header', 'app-header', [
    el('div', 'header-row', [left, opts.right ?? null]),
    opts.below ?? null,
  ])
}
