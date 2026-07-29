import { todayKey } from '../dates'
import { exportBackup, importBackup } from '../storage'
import { button, el, icon } from '../ui'

const RULES: [string, string][] = [
  ['RIR', 'Repetições na reserva. RIR 2 = você pararia faltando 2 reps até a falha.'],
  ['Double progression', 'Fechou todas as séries no topo da faixa com o RIR alvo → suba o menor incremento e volte ao piso da faixa.'],
  ['Incrementos', 'Barra +2kg (1kg/lado) · halteres: próximo par · máquina: +1 placa (se o salto for grande, acumule mais reps antes).'],
  ['Travou?', 'Mantenha a carga e cace +1 rep em qualquer série — isso é progresso.'],
  ['3 sessões sem rep nova', 'Cheque sono e proteína; se ok, troque a variação ou tire 10% e resuba em 3–4 semanas.'],
  ['Deload', 'Semana 8, ou quando cargas caem + sono piora + articulações reclamam + vontade some: mesmos exercícios, metade das séries, −20% de carga, RIR 4, uma semana.'],
  ['Aquecimento', '5 min gerais + 2 séries de aproximação no 1º exercício de cada padrão.'],
  ['Descanso é parte do treino', 'Use o timer. Sem ele, 60 min viram 75.'],
]

function download(filename: string, content: string): void {
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

export function renderRules(root: HTMLElement): void {
  root.innerHTML = ''
  root.append(el('header', 'app-header', el('div', 'header-row', el('h1', '', 'Regras'))))

  const list = el('section', 'block')
  for (const [term, text] of RULES) {
    list.append(el('div', 'rule', [el('strong', 'rule-term', term), el('span', '', text)]))
  }
  root.append(list)

  const fileInput = el('input') as HTMLInputElement
  fileInput.type = 'file'
  fileInput.accept = 'application/json,.json'
  fileInput.style.display = 'none'
  fileInput.addEventListener('change', () => {
    const file = fileInput.files?.[0]
    if (!file) return
    void file.text().then((text) => {
      try {
        JSON.parse(text)
      } catch {
        alert('Arquivo inválido: não é um JSON.')
        return
      }
      if (!confirm('Substituir os dados deste aparelho pelos do backup? O histórico atual será sobrescrito.')) {
        fileInput.value = ''
        return
      }
      try {
        const n = importBackup(text)
        alert(`Backup restaurado: ${n} dia(s) de treino.`)
        location.hash = '#historico'
      } catch (err) {
        alert(`Não deu para importar: ${err instanceof Error ? err.message : 'formato inválido'}.`)
      }
      fileInput.value = ''
    })
  })

  root.append(
    el('div', 'section-title', 'Backup'),
    el('section', 'block', [
      el('div', 'backup-warn', [
        icon('alert', 16),
        el('span', '', 'Seus dados vivem só neste aparelho. Exporte de vez em quando.'),
      ]),
      button('primary-btn', [icon('download', 19), el('span', '', 'Exportar dados')], () => {
        download(`treino-backup-${todayKey()}.json`, exportBackup(todayKey()))
      }),
      button('ghost-btn', [icon('upload', 18), el('span', '', 'Importar dados')], () => fileInput.click()),
      fileInput,
    ]),
  )
}
