# Treino Android: progresso

Plano: `../docs/native-plan.md`. Design (cópia local das telas): `design/*.dc.html`, baixado de
https://claude.ai/artifact/DkTuANNtUVA92i4SdUg1Ff, página "Telas por aba" (`design/canvas.json` tem a ordem).

## Estado

- [x] Esqueleto Gradle (`app` Android + `domain` Kotlin puro), copiado do zazen (AGP 9.1.1, Kotlin 2.4.20,
  compileSdk 37.2, minSdk 26). `./gradlew :domain:test :app:assembleDebug` passa.
- [x] Fase 0: timer nativo (ver `SPIKE.md`; o S23 ainda falta).
- [x] Fase 1: base. Domínio em `:domain`. No `:app`: tema da direção B (`ui/theme`), DM Sans e
  JetBrains Mono variáveis em `res/font` (licenças em `assets/licenses`), componentes
  (`ui/components`), barra de abas e Navigation Compose (`ui/nav`), Room com schema exportado em
  `app/schemas`, `TreinoRepository`, settings em DataStore espelhados no timer, `AppContainer`,
  ícone adaptativo. Telas da Fase 1 são provisórias (`ui/screens/Screens.kt`).
  `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` passa; Room testado em memória
  com Robolectric (SDK 36). Conferido no AVD "Treino": onboarding, abas, Hoje com o treino de
  exemplo, Perfil (interruptor de som grava no `rest_timer`), spike do timer e ícone.
- [ ] Fase 2: importar e exportar
- [ ] Fase 3: telas
- [ ] Fase 4: telas ligadas ao timer

## Decisões tomadas sozinho

- Package e applicationId `dev.charlesferreira.treino`.
- Domínio puro em módulo `:domain` (JVM, sem Android), como o `:engine` do zazen. O import/export também
  mora lá (kotlinx.serialization), para testar sem emulador.
- As telas do design ficam copiadas em `android/design/` (sem as descartadas da página "Arquivo").

- Fase 1, tema: sem Material Theme. `TreinoTheme` só fornece o estilo de texto; os componentes
  desenham o CSS do design direto (`TText` sobre `BasicText`). Material3 não entrou.
- Fontes variáveis do google/fonts com `FontVariation` (um arquivo por família). O DM Sans tem eixo
  de tamanho óptico: texto usa opsz 14 e os títulos (21 sp ou mais) opsz 30, imitando o
  `font-optical-sizing: auto` do navegador.
- Ícones: os paths SVG do design viram `ImageVector` por `addPathNodes`, sem redesenho; círculos e
  retângulos foram convertidos em path. Os pontos (`h.01`) mantêm o traço 3 do design.
- Barra de abas: 24 dp do fundo como no design, ou a barra de navegação + 8 dp quando ela for mais
  alta (3 botões). Telas de aba deixam 104 dp livres embaixo.
- Navegação com rotas tipadas (`@Serializable`). Hoje é a raiz; as outras abas salvam e restauram o
  estado. Descanso, editar série e trocar treino serão sobreposições de Hoje, não rotas.
- Primeiro uso = tabela `exercise` vazia. "Criar meu primeiro treino" grava o nome e
  `seedCatalog()` e abre Treinos. Importar um backup também enche a tabela.
- Room: enums gravados pelo nome serial do backup ("load", "dumbbell"); `aliases` em JSON. Uma sessão
  por dia, criada na primeira escrita com o treino sugerido (o índice em `date` não é único, para não
  travar uma futura sessão dupla). FKs: item → treino e → exercício em cascata; sessão → treino SET
  NULL; série/tempo → sessão em cascata e → exercício NO ACTION (`deleteExercise` devolve false se
  houver histórico).
- `seedCatalog()` usa INSERT OR IGNORE: rodar de novo não desfaz edições num exercício do catálogo.
- `replaceAll` apaga e grava tudo numa transação (um backup quebrado desfaz tudo) e depois aplica
  as preferências que o arquivo trouxer.
- Preferências em DataStore. Som e vibração são copiados para o `RestTimerStore` a cada mudança e na
  abertura do app, porque o timer roda em receivers e não lê DataStore. "Tela sempre ligada" só é
  guardada; o efeito entra com a tela de treino (Fase 3).
- Testes de Room com Robolectric 4.18.1 rodando a imagem SDK 36 (`robolectric.properties`); não
  precisou de teste instrumentado.
- A tela do spike saiu da `MainActivity` e fica em `debug/TimerSpikeScreen.kt`, alcançável só em
  build debug pelo Perfil > Debug, junto com "Criar treino de exemplo" (o Superior B do design).

## Próximo passo

Fase 2 (importar e exportar: `BackupImporter`/`BackupExporter` já existem; falta a tela e o seletor
de arquivo) ou Fase 3 (telas).
