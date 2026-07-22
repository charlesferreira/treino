let sentinel: WakeLockSentinel | null = null
let wanted = false

async function acquire(): Promise<void> {
  try {
    sentinel = await navigator.wakeLock?.request('screen')
  } catch {
    // sem suporte ou negado — segue o jogo
  }
}

/** Mantém a tela acesa enquanto o treino está em andamento. */
export function keepAwake(): void {
  if (wanted) return
  wanted = true
  void acquire()
}

document.addEventListener('visibilitychange', () => {
  if (wanted && document.visibilityState === 'visible' && (!sentinel || sentinel.released)) {
    void acquire()
  }
})
