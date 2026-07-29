import { el } from '../ui'
import { appHeader } from './chrome'

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

export function renderRules(root: HTMLElement): void {
  root.innerHTML = ''
  root.append(appHeader({ title: 'Regras' }))

  const list = el('section', 'block')
  for (const [term, text] of RULES) {
    list.append(el('div', 'rule', [el('strong', 'rule-term', term), el('span', '', text)]))
  }
  root.append(list)
}
