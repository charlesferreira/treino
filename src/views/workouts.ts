import { todayKey } from '../dates'
import { exerciseName } from '../exercises'
import {
  duplicateWorkout,
  findWorkout,
  loadPlan,
  moveWorkout,
  newWorkoutId,
  nextInSequence,
  removeWorkout,
  upsertWorkout,
} from '../plan'
import { loadLogs } from '../storage'
import type { Workout, WorkoutItem } from '../types'
import { button, el, fmtRest, icon } from '../ui'
import { appHeader } from './chrome'
import { defaultItem, openExercisePicker, openItemSheet, openTextSheet } from './pickers'

/* ------------------------------ Lista de treinos ----------------------------- */

export function renderWorkouts(root: HTMLElement): void {
  const rerender = () => renderWorkouts(root)
  const plan = loadPlan()
  const next = nextInSequence(todayKey(), loadLogs())

  root.innerHTML = ''
  root.append(appHeader({
    title: 'Meus treinos',
    back: '#perfil',
    right: button('icon-btn', icon('plus', 20), () => {
      openTextSheet('Novo treino', '', 'Ex.: Superior A', (name) => {
        const id = newWorkoutId(loadPlan())
        upsertWorkout({ id, name, items: [] })
        location.hash = `#treino/${id}`
      })
    }),
  }))

  if (plan.workouts.length === 0) {
    root.append(el('p', 'empty', 'A sequência está vazia. Crie o primeiro treino no + acima.'))
    return
  }

  root.append(el('p', 'hint', 'O app sugere o treino seguinte ao último que você executou. Esta é a ordem do ciclo.'))

  plan.workouts.forEach((w, i) => {
    const sets = w.items.reduce((a, it) => a + it.sets, 0)
    const summary = w.items.length === 0
      ? 'sem exercícios'
      : `${w.items.length} exercício${w.items.length > 1 ? 's' : ''} · ${sets} série${sets > 1 ? 's' : ''}`

    const row = el('section', 'seq-row', [
      el('span', 'seq-n', String(i + 1)),
      button('seq-main', el('span', '', [
        el('div', 'seq-name', [
          el('span', '', w.name),
          w.id === next?.id ? el('span', 'seq-next', 'próximo') : null,
        ]),
        el('div', 'seq-summary', summary),
      ]), () => {
        location.hash = `#treino/${w.id}`
      }),
      el('div', 'seq-moves', [
        button('move-btn', icon('up', 18), () => {
          moveWorkout(w.id, -1)
          rerender()
        }),
        button('move-btn', icon('caret', 18), () => {
          moveWorkout(w.id, 1)
          rerender()
        }),
      ]),
    ])
    root.append(row)
  })
}

/* ------------------------------ Editor de treino ----------------------------- */

function itemMeta(item: WorkoutItem): string {
  if (item.kind === 'tempo') {
    const [lo, hi] = item.minutes ?? [20, 30]
    return [item.sets > 1 ? `${item.sets} × ${lo}–${hi} min` : `${lo}–${hi} min`,
      item.rest > 0 ? fmtRest(item.rest) : ''].filter(Boolean).join(' · ')
  }
  const [lo, hi] = item.reps ?? [8, 12]
  return [`${item.sets} × ${lo}–${hi}`, item.rir ? `RIR ${item.rir}` : '', fmtRest(item.rest)]
    .filter(Boolean).join(' · ')
}

export function renderWorkoutEdit(root: HTMLElement, id: string): void {
  const rerender = () => renderWorkoutEdit(root, id)
  const workout = findWorkout(id)

  root.innerHTML = ''
  if (!workout) {
    root.append(appHeader({ title: 'Treino', back: '#treinos' }))
    root.append(el('p', 'empty', 'Esse treino não existe mais.'))
    return
  }

  const save = (w: Workout) => {
    upsertWorkout(w)
    rerender()
  }

  root.append(appHeader({
    title: workout.name,
    back: '#treinos',
    right: button('icon-btn', icon('note', 19), () => {
      openTextSheet('Nome do treino', workout.name, 'Ex.: Superior A', (name) => {
        save({ ...workout, name })
      })
    }),
  }))

  workout.items.forEach((item, i) => {
    root.append(el('section', 'seq-row', [
      button('seq-main', el('span', '', [
        el('div', 'seq-name', exerciseName(item.exercise)),
        el('div', 'seq-summary', itemMeta(item)),
      ]), () => {
        openItemSheet(
          item,
          (updated) => {
            const items = [...workout.items]
            items[i] = updated
            save({ ...workout, items })
          },
          () => save({ ...workout, items: workout.items.filter((_, j) => j !== i) }),
        )
      }),
      el('div', 'seq-moves', [
        button('move-btn', icon('up', 18), () => {
          if (i === 0) return
          const items = [...workout.items]
          ;[items[i - 1], items[i]] = [items[i], items[i - 1]]
          save({ ...workout, items })
        }),
        button('move-btn', icon('caret', 18), () => {
          if (i === workout.items.length - 1) return
          const items = [...workout.items]
          ;[items[i + 1], items[i]] = [items[i], items[i + 1]]
          save({ ...workout, items })
        }),
      ]),
    ]))
  })

  if (workout.items.length === 0) {
    root.append(el('p', 'empty', 'Nenhum exercício ainda.'))
  }

  root.append(
    button('primary-btn', [icon('plus', 19), el('span', '', 'Adicionar exercício')], () => {
      openExercisePicker((exerciseId) => {
        openItemSheet(defaultItem(exerciseId), (item) => {
          save({ ...workout, items: [...workout.items, item] })
        })
      })
    }),
    el('div', 'section-title', 'Treino'),
    button('ghost-btn', [icon('copy', 17), el('span', '', 'Duplicar treino')], () => {
      const copy = duplicateWorkout(workout.id)
      if (copy) location.hash = `#treino/${copy}`
    }),
    button('ghost-btn danger', [icon('trash', 17), el('span', '', 'Apagar treino')], () => {
      if (!confirm(`Apagar "${workout.name}"? O histórico dos dias em que ele foi feito continua salvo.`)) return
      removeWorkout(workout.id)
      location.hash = '#treinos'
    }),
  )
}
