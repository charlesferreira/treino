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
  - [x] Hoje, primeira parte (`ui/today/`): antes de começar, trocar o treino, treino em andamento
    com o cabeçalho ligado à rolagem, editar série, exercício de tempo, resumo. Testes puros do
    cabeçalho (0, 50 e 100%), do estado do Hoje e do resumo. Conferido no AVD "Treino" contra o
    PNG de cada design.
  - [x] Hoje: descanso expandido, em pílula e no fim (`ui/today/RestViews.kt`), ligado ao timer.
    Conferido no AVD "Treino" contra `b-rest`, `b-rest-pill` e `b-rest-done`.
  - [ ] Evolução, Treinos, Perfil
- [x] Fase 4: timer de verdade. `workout/WorkoutTimer.kt` liga o Room ao timer; regras puras em
  `ui/today/RestPlan.kt` (testes em `RestPlanTest`). No AVD: alarme com a tela apagada disparou 10 ms
  depois do agendado; "Registrar série" na cortina com o processo morto gravou a série no Room e
  começou o descanso seguinte; −15s, +15s e Pular da cortina e do app ficam em sincronia.

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

- Fase 3, Hoje: as folhas (trocar treino, nota, editar série) são desenhadas pela raiz acima da
  barra de abas (`ui/nav/Overlay.kt`), como no design, onde o scrim cobre a barra. Saiu a rota
  `EditSetRoute`; o resumo é a rota `SummaryRoute(date)`.
- Fim do treino: o design não mostra; "Terminar treino" fica no fim da lista (botão cinza, laranja
  quando tudo foi feito) e grava `endedAt`, abrindo o resumo. Depois do fim o cronômetro para e o
  botão vira "Ver resumo".
- Depois do exercício atual vem a lista compacta dos que faltam (número, nome, alvo ou "1/3");
  tocar num feito ou num que falta o torna o atual (num feito, para corrigir ou fazer série extra).
- Pré-preenchimento: a série N vem da mesma série da última sessão (`NextSet.prefill`), mesmo que
  a série anterior de hoje tenha tido outra carga. Exercício nunca feito: a série anterior de hoje,
  senão 0 kg e o início da faixa de reps (10 sem faixa).
- As células de carga e reps da série atual aceitam teclado (o anel laranja segue o campo focado);
  os steppers continuam sendo o caminho principal.
- Cabeçalho: `HeaderScroll.values(p)` é o `renderVals()` do design, p = rolagem / 180 dp. Topo do
  design (54 px) = barra de status + 10 dp, como nas outras telas. Recolhido, o design deixa as
  linhas vazarem 10 px acima do padding, para dentro da área de status vazia do iPhone; aqui o
  cabeçalho cresce esse tanto para não invadir a barra de status do Android. A seta do título e o
  ícone do relógio (que o protótipo de rolagem não tem) somem junto com a data (early).
- Exercício de tempo: minutos em passos de 5, distância livre, local Esteira/Rua (gravado em
  minúsculas). Registrar tempo não inicia descanso. Editar um tempo registrado (fora do design)
  muda só os minutos ou apaga.
- O treino só de tempo mantém a barra e o "0/1" no cabeçalho (o `h-time` não tem barra).
- "Tela sempre ligada": `FLAG_KEEP_SCREEN_ON` só com o treino começado e não terminado.
- `SampleData` (debug) agora cria duas sessões passadas do Superior B (14 e 7 dias antes) e um
  treino "Corrida Z2" com uma corrida 6 dias antes.

- Fase 4, descanso:
  - O descanso começa em `onSetLogged` (`WorkoutTimer.afterSetLogged`): lê o Hoje de novo do Room e
    monta o `RestRequest` (`restAfterLog`). Sem descanso quando o item tem 0 s ou foi a última série
    do treino; nesse caso um descanso que ainda estava na tela é encerrado.
  - "Próxima série" tem uma regra só (`followUp`): o exercício da última série registrada enquanto
    tiver séries, senão o primeiro não completo. O cartão do treino (sem exercício escolhido), a
    folha de descanso e a notificação usam a mesma, então o que a notificação mostra é o que
    "Registrar série" grava (a carga e as reps do pré-preenchimento, `defaultSetDraft`).
  - O store guarda também os textos do app (`nextShort` "16 × 10", `loggedLabel` "Série 1",
    `loggedValue` "16 × 11"). O cabeçalho da folha mostra o exercício da próxima série; quando o
    exercício muda, a linha registrada vira "Puxada, série 3".
  - Expandido x pílula: um descanso que começa com o Hoje aberto abre expandido; um que já corria
    quando o Hoje abriu aparece em pílula. Minimizar: chevron, toque fora ou voltar. A pílula e o
    fim só aparecem no Hoje (as outras abas ainda são provisórias).
  - Fim dentro do app: "Hora da série N" fica até tocar (Abrir limpa o store e o heads-up), até a
    próxima série, ou 10 min. Com o app na frente o serviço toca som e vibração mas não posta o
    heads-up (`RestTimer.appInForeground`, ligado no `onResume`/`onPause`).
  - Notificação entre séries (`RestNotifications.workout`, id 3, canal `rest`): treino, X/Y, cronômetro
    subindo desde `startedAt`, "A seguir: ...", ações "Registrar série" e "Abrir". **Não mantém o
    serviço em primeiro plano**: é uma notificação comum ongoing. O botão é um broadcast, que sobe o
    processo mesmo com o app fechado; o receiver grava com `goAsync` e o toque na notificação dá a
    isenção para iniciar o FGS do descanso seguinte (visto no AVD com o processo morto). Some quando
    o treino termina; num treino só com tempo pendente ou tudo feito ela fica, sem o botão. No
    Android 14+ o usuário pode dispensá-la; ela volta na próxima mudança.
  - Quem atualiza essa notificação: um coletor no `Application` (Room + store, debounce 300 ms) e,
    por garantia, os ganchos `RestTimer.restOverHandler` (Pular e fim do alerta) e o handler do
    "Registrar série", que rodam fora da thread principal.
  - Durante o descanso continuam só −15s, +15s e Pular (limite de 3 ações); "Registrar série" fica
    na notificação entre séries, que aparece junto com o heads-up do fim.
  - "Terminar treino" encerra o descanso em andamento.
  - Permissão de notificação pedida no "Começar treino".

## Próximo passo

Evolução, Treinos e Perfil (Fase 3); a Fase 2 (tela de backup) continua pendente. A Fase 4 ainda
precisa do S23 (`SPIKE.md`, "Para testar no S23", itens 9 a 11).
