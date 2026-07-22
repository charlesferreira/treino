import './style.css'
import { registerSW } from 'virtual:pwa-register'
import { renderToday } from './views/today'
import { renderHistory } from './views/history'
import { renderRules } from './views/rules'
import { button, el } from './ui'

registerSW({ immediate: true })

const app = document.getElementById('app')!
const main = el('main', 'view')

const TABS: { hash: string; label: string; icon: string }[] = [
  { hash: '', label: 'Hoje', icon: '🏋️' },
  { hash: '#historico', label: 'Histórico', icon: '📅' },
  { hash: '#regras', label: 'Regras', icon: '📓' },
]

const tabbar = el('nav', 'tabbar')
for (const tab of TABS) {
  tabbar.append(
    button('tab', el('span', '', [el('span', 'tab-icon', tab.icon), el('span', 'tab-label', tab.label)]), () => {
      location.hash = tab.hash
    }),
  )
}

app.append(main, tabbar)

function render(): void {
  const hash = location.hash
  const idx = hash === '#historico' ? 1 : hash === '#regras' ? 2 : 0
  tabbar.querySelectorAll('.tab').forEach((t, i) => t.classList.toggle('active', i === idx))
  window.scrollTo(0, 0)
  if (idx === 1) renderHistory(main)
  else if (idx === 2) renderRules(main)
  else renderToday(main)
}

window.addEventListener('hashchange', render)
render()
