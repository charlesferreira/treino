import './style.css'
import { registerSW } from 'virtual:pwa-register'
import { bootstrap } from './plan'
import { renderToday } from './views/today'
import { renderHistory } from './views/history'
import { renderRules } from './views/rules'
import { renderProfile } from './views/profile'
import { renderWorkouts, renderWorkoutEdit } from './views/workouts'
import { renderOnboarding } from './views/onboarding'
import { button, el, icon, type IconName } from './ui'

registerSW({ immediate: true })

const app = document.getElementById('app')!

const TABS: { hash: string; label: string; icon: IconName }[] = [
  { hash: '', label: 'Hoje', icon: 'dumbbell' },
  { hash: '#historico', label: 'Histórico', icon: 'calendar' },
  { hash: '#regras', label: 'Regras', icon: 'book' },
]

function startApp(): void {
  app.innerHTML = ''
  const main = el('main', 'view')
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
  const syncHeader = (): void => {
    main.querySelector('.app-header')?.classList.toggle('stuck', window.scrollY > 6)
  }
  window.addEventListener('scroll', syncHeader, { passive: true })
  new MutationObserver(syncHeader).observe(main, { childList: true })

  const render = (): void => {
    const hash = location.hash
    let tab = -1
    if (hash === '#historico') tab = 1
    else if (hash === '#regras') tab = 2
    else if (hash === '' || hash === '#') tab = 0

    tabbar.querySelectorAll('.tab').forEach((t, i) => t.classList.toggle('active', i === tab))
    window.scrollTo(0, 0)

    if (hash === '#historico') renderHistory(main)
    else if (hash === '#regras') renderRules(main)
    else if (hash === '#perfil') renderProfile(main)
    else if (hash === '#treinos') renderWorkouts(main)
    else if (hash.startsWith('#treino/')) renderWorkoutEdit(main, decodeURIComponent(hash.slice(8)))
    else renderToday(main)
    syncHeader()
  }

  window.addEventListener('hashchange', render)
  render()
}

const { needsOnboarding } = bootstrap()
if (needsOnboarding) {
  renderOnboarding(app, () => {
    location.hash = ''
    startApp()
  })
} else {
  startApp()
}
