# Treino Android: progresso

## Para ler de manhã (26 nov 2026)

As fases 0 a 4 estão prontas e verificadas no emulador (AVD "Treino", Android 16). São 115 testes JVM
passando e um teste de ponta a ponta sem crash. Tudo está na `main` do github.com/charlesferreira/treino,
pasta `android/`. O PWA da raiz não mudou.

### Onde está o APK

- `~/Downloads/treino-debug-2026-11-26.apk` (12,4 MB, debug, assinatura de debug).
- Para gerar de novo: `cd android && ./gradlew :app:assembleDebug`. O arquivo sai em
  `app/build/outputs/apk/debug/app-debug.apk`.
- Release assinado (Fase 5, 26 nov): `~/Downloads/treino-release-2026-11-26.apk` (2,1 MB, R8 ligado),
  instalado no S23 sobre uma instalação limpa. Ver "Release assinado" abaixo.

### O que testar no S23, passo a passo

1. **Instalar e migrar o PWA.** Instale com `adb install -r ~/Downloads/treino-debug-2026-11-26.apk`.
   No PWA do celular, exporte o backup. Abra o Treino e siga "Já tenho um backup" e "Importar arquivo".
   Confira a prévia (dias, séries, treinos, exercícios, período) e toque em Importar. Esse é o primeiro
   teste com um backup real: até agora só foram usados backups sintéticos no formato do PWA.
2. **Ver os dados importados:**
   - Evolução > Dias: os dias aparecem; os dias importados ficam sem duração.
   - Evolução > Exercícios: carga máxima e "subiu".
   - Treinos: a sequência.
3. **Equipamento.** Os exercícios entram como halteres só quando têm "halter" no nome. Se algum for de
   halteres sem a palavra (por exemplo, Rosca martelo), corrija em Evolução > exercício > selo de
   equipamento, ou em Treinos > Todos os exercícios.
4. **Revisar as cargas antigas.** Em Perfil > Revisar cargas antigas, confira as sessões sinalizadas
   (carga de cerca de 1,6× a mediana das vizinhas). Nada muda sem a sua escolha.
5. **Um treino de verdade.** Em Hoje, toque em Começar treino e registre uma série. O descanso abre
   expandido. Minimize, bloqueie o celular e espere o fim. Deve vibrar mesmo no silencioso, tocar os 3
   bipes (no volume de mídia) e mostrar o heads-up.
6. **Itens 1 a 11 de "Para testar no S23", em `docs/SPIKE.md`.** Lá estão Now Bar e Live Update,
   botões na tela bloqueada, mídia em 0, silencioso e Não perturbe, música abaixando, heads-up laranja,
   Doze natural, notificação entre séries e "Registrar série" com o app fechado. Anote os resultados
   no próprio SPIKE.md.
7. **Exportar.** Em Perfil > Backup > Exportar dados, salve o arquivo (format 3). Ele também abre no PWA.

### Decisões que tomei sozinho (detalhes em "Decisões" abaixo)

- **Foco de áudio.** O som do fim pede `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`: a música da academia
  abaixa em vez de pausar. O som continua `USAGE_MEDIA`, como no plano.
- **Notificação entre séries.** É uma notificação comum, sem serviço em primeiro plano. O "Registrar
  série" é um broadcast que grava no Room com o app fechado.
- **Série registrada pela notificação.** Usa a carga e as reps pré-preenchidas, exatamente o que a
  notificação mostra.
- **Pré-preenchimento.** Repete a mesma série da última sessão. Num exercício nunca feito, repete a
  série anterior de hoje.
- **Passos por equipamento.** Halter 2 kg, barra 2 kg, máquina 1 placa (placa de 5 kg, ajustável).
  Também decidi: cabo 2,5 kg, peso corporal 2 kg, outro 1 kg.
- **Equipamento na importação.** É deduzido do nome; "halter" liga o "conta 2 halteres".
- **Detector de cargas suspeitas:**
  - olha só exercícios de halteres;
  - compara com até 3 sessões antes e 3 depois, e exige pelo menos 2 vizinhas;
  - sinaliza a partir de 1,6×;
  - as sessões decididas ficam lembradas no aparelho e não vão no backup.
- **UUIDs.** São determinísticos a partir dos ids do PWA, então reimportar não duplica nada.
- **Format 3.** É o format 2 completo, que o PWA lê, mais um bloco `native` com equipamento, passo,
  horários e UUIDs.
- **Cabeçalho que encolhe.** Segue o `renderVals()` do design. Mudei uma coisa: o fundo chega a 0,94
  na metade da rolagem, e não no fim. Com texto de verdade por trás, a lista aparecia demais.
- **Telas da direção A.** A folha de equipamento e a revisão de cargas foram desenhadas na direção A
  (verde, Barlow). Fiz com as cores e fontes da direção B e mantive o layout e os textos.
- **Pequenos acréscimos fora do design:**
  - folha "Nota do dia";
  - editar um tempo registrado;
  - botão "Terminar treino" no fim da lista;
  - menu ⋮ do treino (Renomear, Duplicar, Apagar);
  - "Já tenho um backup" no primeiro uso.
- **Traço nas faixas.** Mantive o "–" nas faixas numéricas ("8–12"), como o design usa. Fora delas,
  não há "·" nem travessão nas telas.
- **Fim do descanso no emulador.** O heads-up não saiu laranja: o `setColorized` foi ignorado. O
  código continua pedindo cor e falta conferir na One UI.

### O que ficou faltando

- Tudo que só o S23 valida (item 6 acima).
- Teste com um backup real do PWA (item 1 acima).
- Fase 5: instalar o release no celular da irmã.
- Fase 6: a troca nos dois celulares.
- Detalhes conhecidos:
  - a barra da notificação enche em linha reta pelo total de séries, não por exercício;
  - um exercício repetido no mesmo treino mostra as mesmas séries nos dois itens;
  - não há compartilhar (`ACTION_SEND`) no Backup, porque o design não mostra.

---

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
- [x] Fase 2: importar e exportar. Tela Backup (`ui/profile/BackupScreen.kt`): exporta o format 3 pelo
  "Salvar como" do sistema e importa pelo seletor de arquivos com a prévia antes de substituir.
  No AVD: um backup do PWA (format 2, 70 dias de 22 jul a 20 nov, com `cardio` antigo) importado
  pelo seletor, conferido em Hoje, Evolução e Treinos; exportado, puxado com `adb pull`, conferido e
  reimportado pelo app com os mesmos números. `BackupRoundTripTest` (Robolectric) faz importar →
  exportar → importar e compara as tabelas.
- [ ] Fase 3: telas
  - [x] Hoje, primeira parte (`ui/today/`): antes de começar, trocar o treino, treino em andamento
    com o cabeçalho ligado à rolagem, editar série, exercício de tempo, resumo. Testes puros do
    cabeçalho (0, 50 e 100%), do estado do Hoje e do resumo. Conferido no AVD "Treino" contra o
    PNG de cada design.
  - [x] Hoje: descanso expandido, em pílula e no fim (`ui/today/RestViews.kt`), ligado ao timer.
    Conferido no AVD "Treino" contra `b-rest`, `b-rest-pill` e `b-rest-done`.
  - [x] Evolução (`ui/evolution/`): Dias (calendário de 3 semanas e dias com séries, duração e
    volume), Dia do histórico (com "Editar"), Exercícios (3 números de 30 dias, filtro por grupo,
    lista com mini-gráfico) e Progressão do exercício (gráfico em Canvas com Carga máx, Volume e
    Melhor série). Estado puro testado em `EvolutionStateTest` e escala do gráfico em
    `ChartMathTest`. Conferido no AVD "Treino" contra o PNG de cada design.
  - [x] Treinos (`ui/workouts/`): sequência com "próximo" e arrastar para reordenar, criar treino,
    editar treino (itens, reordenar, adicionar, renomear, duplicar, apagar), exercício no treino
    (folha), adicionar exercício (busca com sinônimos e sem acento, grupos do PWA, criar), "Todos os
    exercícios" e a folha de equipamento, aberta do item, da lista e do selo da Progressão. Estado
    puro em `WorkoutsStateTest` e `ExerciseSearchTest`; Room em `WorkoutsRepositoryTest`. Conferido no
    AVD "Treino" contra o PNG de cada design, e no Hoje: "KG/HALTER", passo ±2 e ±2,5 e o treino
    sugerido seguindo a nova ordem.
  - [x] Perfil (`ui/profile/`): Perfil, Revisar cargas de halteres, Backup e Primeiro uso. Estado puro
    em `ProfileStateTest`. Conferido no AVD "Treino" contra o PNG de cada design, com a revisão
    aplicando e mantendo sessões de verdade.
- [x] Fase 4: timer de verdade. `workout/WorkoutTimer.kt` liga o Room ao timer; regras puras em
  `ui/today/RestPlan.kt` (testes em `RestPlanTest`). No AVD: alarme com a tela apagada disparou 10 ms
  depois do agendado; "Registrar série" na cortina com o processo morto gravou a série no Room e
  começou o descanso seguinte; −15s, +15s e Pular da cortina e do app ficam em sincronia.
- [x] Polimento e teste de ponta a ponta (26 nov), no AVD "Treino" com `pm clear`: primeiro uso →
  "Já tenho um backup" (sem Exportar) → backup format 2 de 70 dias → Hoje → 2 séries → descanso →
  segundo plano, tela apagada e Doze forçado: alarme 27 ms depois do agendado (`SPIKE.md`) →
  "Registrar série" na cortina gravou a série 3 → resumo → Evolução com 26 nov → export format 3 (71
  dias) puxado e conferido → reimportado com um descanso correndo: descanso, alarme e notificações
  sumiram. Sem crash no `logcat -b crash`. `:domain:test :app:testDebugUnitTest :app:assembleDebug
  :app:assembleRelease` passam (release sem assinatura).
- [x] Fase 5: release assinado (26 nov). Keystore `~/.android-keys/treino-release.jks` (PKCS12, RSA 4096,
  alias `treino`, válida até 2054, SHA-256 do certificado `E8:A8:12:2F:…:F9:08:45`), com cópia do
  `keystore.properties` em `~/.android-keys/treino-keystore.properties`. **Perder a keystore impede
  atualizar o app instalado sem desinstalar (e perder os dados).** Gerar: copiar o
  `keystore.properties` para `android/` e `./gradlew :app:assembleRelease`; sai em
  `app/build/outputs/apk/release/app-release.apk`, assinado v2. Debug e release têm assinaturas
  diferentes: trocar um pelo outro exige `adb uninstall dev.charlesferreira.treino` (apaga os dados;
  exporte antes). No S23 (SM-S918B), sem Treino instalado: `adb install` do release, primeiro uso →
  "Já tenho um backup" → `treino-backup-2026-11-25.json` do PWA (Downloads) → prévia "11 dias de
  treino, 168 séries, 7 treinos, 31 exercícios, 22 jul a 12 ago" → Importar → Evolução com 12 ago,
  11 ago, 5 ago... Sem crash no `logcat -b crash` (o R8 não quebrou o kotlinx.serialization).
- [x] Backup real do PWA (26 nov). Os dois arquivos de 25 nov do S23 (`treino-backup-2026-11-25.json`,
  7 treinos, e `-superiores.json`, 3 treinos Superior A/B/C) passam pelo importador com os mesmos
  números que o JSON (167 séries, 1 tempo, 12 e 11 dias no log). Achado: o PWA deixa no log o dia em
  que só se escolheu o treino ou em que a última série foi apagada (`"2026-11-25": {template,
  sets: {}}`). A sessão continua entrando (guarda o treino escolhido do dia, como o PWA usa na
  sugestão), mas a prévia agora conta dias como Evolução > Dias: com série, tempo ou nota (dizia 12).

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

- Fase 3, Evolução:
  - Calendário dos Dias: as 3 últimas semanas (segunda primeiro, a atual por último), como o design
    desenha; o título é o mês de hoje e o número conta os dias com treino nesse mês. Laranja = dia
    com carga, anel laranja = só tempo, anel claro = hoje.
  - Lista dos Dias: um cartão por mês; os meses anteriores ganham o nome em cima (`OUTUBRO`), o
    atual já está no calendário. Dia de carga: séries (séries + tempos), duração só quando há
    horário (importados não têm) e volume em kg inteiros. Dia só de tempo: minutos, km e local.
    Sem treino ligado (apagado): o nome do primeiro exercício.
  - As linhas "máx 18 kg" ficam no dia de carga mais recente (uma corrida depois dele não tem o
    que mostrar), até 3, as que subiram primeiro, na ordem em que foram feitas. Ali a unidade fica
    curta como no design ("máx 18 kg"); o "kg por halter" aparece no Dia, na lista de Exercícios
    ("por halter") e na Progressão ("Carga máxima por halter").
  - Dia do histórico: o volume é rotulado ("volume 9.840 kg") apesar do design mostrar só o número,
    pela regra do plano. "Editar" vira "Pronto" e transforma cada série/tempo em botão que abre as
    folhas do Hoje (`EditSetSheet`, `EditTimeSheet`) e a nota em botão da `NoteSheet` (sem nota,
    aparece "Nota do dia"). Ordem dos exercícios: a do treino, depois a hora da primeira série.
  - Exercícios: só exercícios de carga com série. "Subiu" = a última subida foi nos últimos 30 dias
    e a carga ficou lá desde então: texto e mini-gráfico laranja, "+2 kg em 26 nov" (placas na
    máquina). Senão "igual desde 3 nov" cinza; uma sessão só: "1ª vez em 3 nov". O número
    "Subiram" conta essas linhas (o design mostra um "igual desde 3 nov" dentro de 30 dias; aqui
    ficaria laranja). Ordem: as que subiram, da subida mais recente; depois pela última sessão. O
    número à direita é a carga máxima da última sessão. Treinos = dias com registro em 30 dias;
    Volume = 30 dias contra os 30 anteriores ("–" sem base).
  - Filtro: Superior = peito, costas, ombros; Pernas = quadríceps, posteriores, glúteos,
    panturrilha; Braços = bíceps, tríceps, antebraço; o resto só em Todos. Só aparecem os grupos
    que têm exercício, e a fileira some se só sobrar Todos.
  - Mini-gráfico: as 7 últimas sessões, maior em y 4 e menor em 22; plano em 13.
  - Progressão: o número grande é a carga máxima de todas as sessões. O gráfico mostra as 8 últimas
    (Carga máx; Volume do exercício; Melhor série = carga × reps da melhor série da sessão), escala
    do SVG do design (valor maior na linha 30, menor na 100, meio na 65; plano no meio com um passo
    acima e abaixo), datas em pontos alternados. "+4 kg em 7 sem" compara a primeira e a última
    dessas sessões ("igual em", "dias" abaixo de 7). "Mais reps com X": X é a carga logo abaixo da
    máxima (a própria se nunca mudou), na sessão com mais reps nela. A lista mostra todas as
    sessões, "↑" quando a carga subiu. O selo de equipamento vem do exercício ("Halteres 2×",
    "Máquina", "Polia", "Barra"...); "outro" fica sem selo.
  - A Progressão mantém a barra de abas, como no design; tocar Evolução nela volta para a raiz da aba.
  - `SampleData` (debug): Superior B por 9 semanas (63 a 7 dias atrás) e Pernas A (leg press e
    extensora na máquina, stiff com halteres) dois dias antes de cada uma, cargas subindo um passo
    a cada 3 semanas; as 2 sessões mais antigas de cada são "importadas" (sem horário); corrida
    semanal (a última 6 dias atrás, a mais velha sem km nem local); nota no Superior B de 7 dias
    atrás. A última sessão do Superior B e a corrida de 6 dias atrás continuam como antes, então o
    Hoje segue sugerindo o Superior B. `logSet`/`logTime` aceitam `loggedAt` nulo para isso.

- Fase 3, Treinos:
  - Reordenar arrastando a alça (sem biblioteca, `ui/workouts/Reorderable.kt`): a linha segue o dedo,
    as outras abrem espaço e a nova ordem aparece na hora, antes do Room responder. TalkBack ganha
    "Mover para cima/baixo" em cada linha. As listas são curtas, então é uma `Column`, não lazy.
  - "próximo" (`WorkoutSequence.upcoming`): o que o Hoje mostra enquanto hoje não tem registro;
    depois do primeiro registro de hoje, o seguinte na sequência.
  - Exercício no treino e equipamento são folhas, não rotas: saíram `WorkoutItemEditRoute` e
    `ExerciseEditRoute`. `ExercisePickerRoute(workoutId = null)` é "Todos os exercícios" (título
    "Exercícios", cada linha com o equipamento; tocar abre a folha de equipamento).
  - O tipo (Carga × reps / Tempo) mora no exercício: trocar no item grava o exercício. A folha guarda
    as duas faixas (reps e minutos) e só grava a do tipo escolhido. Faixa: um campo só vale para os
    dois lados, invertida é trocada, reps inteiras de 1 a 999, minutos com vírgula até 600; inválida
    deixa a borda vermelha e o Salvar apagado. Notas do item ficam num campo abaixo do descanso (o
    design não mostra). Descanso de 15 em 15 s, 0 = "sem". "Remover do treino" remove sem perguntar
    (o item não tem histórico).
  - Item novo pelo seletor: 3 × 8–12 com 2:00, ou 1 × 20–30 min sem descanso (o `defaultItem` do
    PWA); volta para o treino com a folha do item aberta. "Criar “…”" cria em "Meus exercícios", de
    carga, com o equipamento deduzido do nome.
  - "cerca de N min": 1 min por série mais o descanso dela, 1 min por exercício, o meio da faixa nos
    de tempo; arredondado a 5.
  - Folha de equipamento: o design (`dumbbell-equipment`) é da direção A (verde, Barlow); foi
    redesenhada com as cores e fontes da B, mesmo layout e textos. O quadro "Máquina, para comparar"
    virou o campo "Peso da placa" (±0,5 kg), que só aparece na máquina, onde o passo ganha a opção
    "1 placa" (padrão). "Volume conta 2 halteres" só aparece em halteres (ligado ao escolher
    halteres). Escolher o passo padrão do equipamento grava nulo. Prescrição: a do item de onde
    veio; na Progressão, a do primeiro treino da sequência que tem o exercício; na lista, nada.
    Salvar a partir do item grava o exercício e o rascunho do item; o X volta para o item.
  - Na Progressão o selo de equipamento abre a folha; sem equipamento ("outro") aparece "Equipamento".
  - Apagar treino pede confirmação numa folha ("Os dias em que ele foi feito continuam no
    histórico."); duplicar cria "<nome> (cópia)" logo depois do original e abre a cópia.

- Fase 3, Perfil e Fase 2:
  - Perfil: tocar no nome ou no avatar abre a folha "Seu nome" (como o PWA). Sem nome: "Quem está
    treinando?". "treinando desde" = o primeiro dia com registro; ano só quando não é o atual. Revisão
    com 0 sessões: "nada para conferir" e chevron no lugar do selo. Backup: "último em 20 nov" ou
    "nenhum ainda". Debug (spike e treino de exemplo) continua no fim, só no build debug.
  - Revisão das cargas: o design é da direção A; mesmo layout e textos nas cores da B, como a folha de
    equipamento (lima → laranja, Barlow → JetBrains Mono, título sem caixa alta no DM Sans de 30). O
    âmbar do "anotado" (#FFB454) ficou, porque a B não tem cor para isso. Um exercício por vez, na
    ordem de quando a tela abriu ("1 de 2" só com mais de um). Linhas: cada sessão sinalizada com a
    vizinha antes e depois; gráfico: até 3 sessões antes da primeira e depois da última. A linha "Com
    as correções" segue as escolhas (sem escolha = corrigida; "Por halter" = como está); rótulos na
    carga máxima anotada e na metade dela. Nada fica escolhido de início. "Aplicar N correção(ões)"
    grava as divisões (`updateSets`, uma transação) e lembra as sessões decididas; só "Por halter"
    escolhido vira "Confirmar"; sem escolha o botão fica apagado. "Manter tudo como está" lembra
    todas as sinalizadas do exercício. Sem decisão, a sessão volta da próxima vez. Acabou tudo: volta
    ao Perfil. Sem nada: "Nenhuma carga para conferir.".
  - Sessões decididas (mantidas ou corrigidas) ficam no DataStore (`reviewed_loads`, chave
    `sessão:exercício`). Como os UUIDs do import são determinísticos, reimportar o mesmo backup não
    traz de volta o que já foi decidido. Isso não viaja no backup.
  - Backup: data do último export no DataStore (`last_export`), gravada só quando o arquivo foi
    escrito. O design não mostra compartilhar, então não há `ACTION_SEND` nem FileProvider. Prévia:
    "backup do app web" (format 1, 2, cru) ou "backup deste app" (format 3), ", exportado em 20 nov"
    quando o arquivo diz; séries = séries + tempos (como em Dias); singulares; período com ano quando
    não é tudo do ano atual; sem programa no arquivo: "O arquivo não traz treinos: entram os do
    programa de exemplo.". Erro: a mensagem da exceção em laranja claro no cartão do arquivo. Depois
    de importar, a pilha recomeça em Hoje e abre Evolução (vale para o primeiro uso também).
  - `PrimaryButton` ganhou `fontSize`, `iconSize` e `iconStroke` opcionais; `TextInput`,
    `keyboardActions`. `OnboardingScreen` mudou para `ui/profile/` sem mudar o layout (já batia com o
    design); `Screens.kt` ficou só com `SubScreenHeader`.

- Polimento (26 nov):
  - Cabeçalho do treino: a geometria segue o protótipo (conteúdo em 162, anda 180), mas o fundo chega
    a 0,94 na metade da rolagem (`0,94 × early`) em vez de no fim. No protótipo o conteúdo são cartões
    vazios quase da cor do fundo, então 47% a 50% parece opaco; com texto de verdade a lista ficava
    legível por trás das linhas.
  - A lista do treino tem altura mínima de tela + 180 dp, para um treino curto também recolher o
    cabeçalho. `TabBarDefaults.contentClearance()` substitui os 104 dp fixos: cresce com a barra de 3
    botões (conferido no AVD). Perfil não tem barra de abas; já termina acima da barra do sistema.
  - Importar (`replaceAll`) chama `WorkoutTimer.resetForImport`: `RestTimer.skip` e tira as
    notificações de descanso, de fim e entre séries. Se o arquivo tiver um treino de hoje em
    andamento, o coletor posta a notificação de novo com os dados novos.
  - `BackupRoute(firstUse)`: aberto pelo primeiro uso, o Backup não mostra Exportar.
  - Texto: faixas numéricas ficam com o traço de faixa ("8–12", "30–40 min"), como no design aprovado.
    Os "–" usados como valor vazio viraram vazio (células de série) ou "-" (blocos de número). Nas
    notas do programa de exemplo (cópia do PWA, só semente) saíram o travessão, o RIR e a dica de
    progressão; o `src/program.json` do PWA não mudou.

## Próximo passo

Fases 0 a 5 prontas e o release assinado está no S23 com os dados do PWA. Falta anotar no
`SPIKE.md` os testes do aparelho ("Para testar no S23", itens 1 a 11) e corrigir o que falhar;
depois a Fase 6 (a troca nos dois celulares).
