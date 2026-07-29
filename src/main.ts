import './style.css'
import { registerSW } from 'virtual:pwa-register'
import { renderToday } from './views/today'
import { renderHistory } from './views/history'
import { renderRules } from './views/rules'
import { button, el, icon, type IconName } from './ui'

registerSW({ immediate: true })

const app = document.getElementById('app')!
const main = el('main', 'view')

const TABS: { hash: string; label: string; icon: IconName }[] = [
  { hash: '', label: 'Hoje', icon: 'dumbbell' },
  { hash: '#historico', label: 'Histórico', icon: 'calendar' },
  { hash: '#regras', label: 'Regras', icon: 'book' },
]

const tabbar = el('nav', 'tabbar')
for (const tab of TABS) {
  tabbar.append(
    button('tab', [icon(tab.icon, 22), el('span', 'tab-label', tab.label)], () => {
      location.hash = tab.hash
    }),
  )
}

app.append(main, tabbar)

// Hairline sob o cabeçalho fixo só depois que a tela rola.
function syncHeader(): void {
  main.querySelector('.app-header')?.classList.toggle('stuck', window.scrollY > 6)
}
window.addEventListener('scroll', syncHeader, { passive: true })
new MutationObserver(syncHeader).observe(main, { childList: true })

function render(): void {
  const hash = location.hash
  const idx = hash === '#historico' ? 1 : hash === '#regras' ? 2 : 0
  tabbar.querySelectorAll('.tab').forEach((t, i) => t.classList.toggle('active', i === idx))
  window.scrollTo(0, 0)
  if (idx === 1) renderHistory(main)
  else if (idx === 2) renderRules(main)
  else renderToday(main)
  syncHeader()
}

window.addEventListener('hashchange', render)
render()
