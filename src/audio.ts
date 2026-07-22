let ctx: AudioContext | null = null

/** Chamar num gesto do usuário (registrar série) para o iOS liberar o áudio. */
export function unlockAudio(): void {
  try {
    ctx ??= new AudioContext()
    if (ctx.state === 'suspended') void ctx.resume()
  } catch {
    // sem áudio, sem drama
  }
}

function tone(at: number, freq: number, dur: number): void {
  if (!ctx) return
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = 'sine'
  osc.frequency.value = freq
  gain.gain.setValueAtTime(0.0001, at)
  gain.gain.exponentialRampToValueAtTime(0.5, at + 0.02)
  gain.gain.exponentialRampToValueAtTime(0.0001, at + dur)
  osc.connect(gain).connect(ctx.destination)
  osc.start(at)
  osc.stop(at + dur + 0.05)
}

/** Beep triplo de fim de descanso. */
export function beep(): void {
  try {
    unlockAudio()
    if (!ctx) return
    const t = ctx.currentTime
    tone(t, 880, 0.15)
    tone(t + 0.22, 880, 0.15)
    tone(t + 0.44, 1320, 0.3)
  } catch {
    // silêncio
  }
}

export function vibrate(pattern: number[]): void {
  try {
    navigator.vibrate?.(pattern)
  } catch {
    // iOS não vibra — o beep e o flash visual cobrem
  }
}
