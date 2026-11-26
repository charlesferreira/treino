# Treino Android: progresso

Plano: `../docs/native-plan.md`. Design (cópia local das telas): `design/*.dc.html`, baixado de
https://claude.ai/artifact/DkTuANNtUVA92i4SdUg1Ff, página "Telas por aba" (`design/canvas.json` tem a ordem).

## Estado

- [x] Esqueleto Gradle (`app` Android + `domain` Kotlin puro), copiado do zazen (AGP 9.1.1, Kotlin 2.4.20,
  compileSdk 37.2, minSdk 26). `./gradlew :domain:test :app:assembleDebug` passa.
- [ ] Fase 0: timer nativo
- [ ] Fase 1: base, tema, Room, domínio
- [ ] Fase 2: importar e exportar
- [ ] Fase 3: telas
- [ ] Fase 4: telas ligadas ao timer

## Decisões tomadas sozinho

- Package e applicationId `dev.charlesferreira.treino`.
- Domínio puro em módulo `:domain` (JVM, sem Android), como o `:engine` do zazen. O import/export também
  mora lá (kotlinx.serialization), para testar sem emulador.
- As telas do design ficam copiadas em `android/design/` (sem as descartadas da página "Arquivo").

## Próximo passo

Fase 0.
