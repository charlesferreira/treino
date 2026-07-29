import { todayKey } from '../dates'
import { initials, loadPlan, loadProfile, saveProfile, seedFromProgram, hasPlan } from '../plan'
import { exportBackup, importBackup, loadSettings } from '../storage'
import { button, el, icon } from '../ui'
import { appHeader } from './chrome'
import { openTextSheet } from './pickers'

function download(filename: string, content: string): void {
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

/** Input de arquivo + confirmação. `onDone` roda depois de importar. */
export function backupFileInput(onDone: () => void): HTMLInputElement {
  const input = el('input') as HTMLInputElement
  input.type = 'file'
  input.accept = 'application/json,.json'
  input.style.display = 'none'
  input.addEventListener('change', () => {
    const file = input.files?.[0]
    if (!file) return
    void file.text().then((text) => {
      try {
        JSON.parse(text)
      } catch {
        alert('Arquivo inválido: não é um JSON.')
        return
      }
      if (!confirm('Substituir os dados deste aparelho pelos do backup? O que está aqui será sobrescrito.')) {
        input.value = ''
        return
      }
      try {
        const { days, hasPlan: planInBackup } = importBackup(text)
        // Backup antigo (só histórico): monta o programa a partir do exemplo,
        // que usa os mesmos ids de treino que aqueles logs guardam.
        if (!planInBackup && !hasPlan()) seedFromProgram()
        alert(`Backup restaurado: ${days} dia(s) de treino.`)
        onDone()
      } catch (err) {
        alert(`Não deu para importar: ${err instanceof Error ? err.message : 'formato inválido'}.`)
      }
      input.value = ''
    })
  })
  return input
}

export function renderProfile(root: HTMLElement): void {
  const rerender = () => renderProfile(root)
  const profile = loadProfile()
  const plan = loadPlan()
  const settings = loadSettings()

  root.innerHTML = ''
  root.append(appHeader({ title: 'Perfil', back: '' }))

  const ini = initials(profile.name)
  root.append(el('div', 'profile-head', [
    el('div', 'avatar avatar-lg', ini ? el('span', '', ini) : icon('user', 30)),
    button('profile-name', [
      el('span', '', profile.name || 'Quem está treinando?'),
      icon('note', 16),
    ], () => {
      openTextSheet('Seu nome', profile.name, 'Como te chamam', (name) => {
        saveProfile({ ...profile, name })
        rerender()
      })
    }),
  ]))

  root.append(button('nav-row', [
    el('span', '', [
      el('div', 'nav-title', 'Meus treinos'),
      el('div', 'nav-sub', plan.workouts.length === 0
        ? 'nenhum treino'
        : `${plan.workouts.length} treino${plan.workouts.length > 1 ? 's' : ''} na sequência`),
    ]),
    icon('chevron', 18),
  ], () => {
    location.hash = '#treinos'
  }))

  const fileInput = backupFileInput(() => {
    location.hash = '#historico'
  })

  root.append(
    el('div', 'section-title', 'Backup e dados'),
    el('section', 'block', [
      el('div', 'backup-warn', [
        icon('alert', 16),
        el('span', '', settings.lastExport
          ? `Seus dados vivem só neste aparelho. Último backup: ${settings.lastExport}.`
          : 'Seus dados vivem só neste aparelho. Exporte de vez em quando.'),
      ]),
      button('primary-btn', [icon('download', 19), el('span', '', 'Exportar dados')], () => {
        download(`treino-backup-${todayKey()}.json`, exportBackup(todayKey()))
        rerender()
      }),
      button('ghost-btn', [icon('upload', 18), el('span', '', 'Importar dados')], () => fileInput.click()),
      fileInput,
    ]),
  )
}
