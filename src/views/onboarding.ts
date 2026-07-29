import { exerciseName } from '../exercises'
import { savePlan, saveProfile } from '../plan'
import type { Workout, WorkoutItem } from '../types'
import { button, el, icon } from '../ui'
import { backupFileInput } from './profile'
import { defaultItem, openExercisePicker, openItemSheet } from './pickers'

/**
 * Só roda em aparelho sem nenhum dado: pergunta o nome e monta o primeiro treino.
 * Quem já tem histórico nunca passa por aqui.
 */
export function renderOnboarding(root: HTMLElement, done: () => void): void {
  let name = ''
  const workout: Workout = { id: 'treino-1', name: 'Treino A', items: [] }

  const stepName = (): HTMLElement => {
    const input = el('input', 'text-input') as HTMLInputElement
    input.type = 'text'
    input.placeholder = 'Seu nome'
    input.value = name
    input.autocapitalize = 'words'

    const go = () => {
      name = input.value.trim()
      if (!name) return
      draw(stepWorkout)
    }
    const next = button('primary-btn', 'Continuar', go)
    input.addEventListener('keydown', (ev) => {
      if (ev.key === 'Enter') go()
    })

    const fileInput = backupFileInput(done)

    const box = el('div', 'onb', [
      el('div', 'onb-mark', icon('dumbbell', 30)),
      el('h1', 'onb-title', 'Bora treinar.'),
      el('p', 'onb-sub', 'Registro de série, progressão e descanso — tudo neste aparelho, sem conta.'),
      el('h2', 'onb-question', 'Como você se chama?'),
      input,
      next,
      button('ghost-btn', 'Já tenho um backup', () => fileInput.click()),
      fileInput,
    ])
    setTimeout(() => input.focus(), 80)
    return box
  }

  const stepWorkout = (): HTMLElement => {
    const nameInput = el('input', 'text-input') as HTMLInputElement
    nameInput.type = 'text'
    nameInput.value = workout.name
    nameInput.placeholder = 'Nome do treino'
    nameInput.addEventListener('input', () => (workout.name = nameInput.value))

    const list = el('div', 'onb-items')
    for (const [i, item] of workout.items.entries()) {
      list.append(button('seq-main', el('span', '', [
        el('div', 'seq-name', exerciseName(item.exercise)),
        el('div', 'seq-summary', item.kind === 'tempo'
          ? `${item.minutes?.[0]}–${item.minutes?.[1]} min`
          : `${item.sets} × ${item.reps?.[0]}–${item.reps?.[1]}`),
      ]), () => {
        openItemSheet(item, (updated) => {
          workout.items[i] = updated
          draw(stepWorkout)
        }, () => {
          workout.items.splice(i, 1)
          draw(stepWorkout)
        })
      }))
    }

    const add = button('primary-btn', [icon('plus', 19), el('span', '', 'Adicionar exercício')], () => {
      openExercisePicker((exerciseId) => {
        openItemSheet(defaultItem(exerciseId), (item: WorkoutItem) => {
          workout.items.push(item)
          draw(stepWorkout)
        })
      })
    })

    const finish = button('primary-btn', 'Começar a treinar', () => {
      saveProfile({ name })
      savePlan({ version: 1, workouts: [workout] })
      done()
    })

    return el('div', 'onb', [
      el('span', 'eyebrow', `Prazer, ${name.split(' ')[0]}`),
      el('h1', 'onb-title', 'Seu primeiro treino'),
      el('p', 'onb-sub', 'Dá para criar quantos quiser depois — o app sugere sempre o próximo da sequência.'),
      nameInput,
      list,
      add,
      workout.items.length > 0 ? finish : null,
    ])
  }

  const draw = (step: () => HTMLElement) => {
    root.innerHTML = ''
    root.append(step())
  }

  draw(stepName)
}
