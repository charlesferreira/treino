# Treino nativo (Android, Kotlin)

Plano aprovado em 26 nov 2026. Ele substitui o redesign do PWA. O PWA continua no ar como está, sem mudanças, até a troca.

Design aprovado (direção B, "Brasa"): https://claude.ai/artifact/DkTuANNtUVA92i4SdUg1Ff, página "Telas por aba". A página "Arquivo" guarda a direção A, o iPhone e as alternativas de menu, todos descartados.

## Decisões

- Só Android, 100% nativo: Kotlin e Jetpack Compose, sem React Native e sem iOS. A abordagem do timer é a mesma do zazen (`~/Code/charles/zazen`, ver `docs/SPIKE.md`).
- Menu inferior com três abas:
  - **Hoje**;
  - **Evolução**, com o seletor Dias / Exercícios (junta o antigo Histórico e o Progresso);
  - **Treinos**, com a sequência, os editores e a lista de exercícios.
  - O avatar abre o Perfil: revisão de cargas antigas, som, vibração, tela ligada e backup.
- Sem orientação de progressão: sai o `progression.ts`, sai a aba Regras, sai a configuração `hideProgressionTips`. A próxima série vem preenchida com a carga e as reps da última sessão. O app mostra fatos: carga máxima, "subiu de carga", "+2 kg em 26 nov".
- Sem RIR: o campo sai do modelo e das telas. Na importação, o `rir` das séries antigas é ignorado.
- Halteres: a carga é digitada por halter, e o volume conta ×2 quando o exercício tem "conta 2 halteres". O passo do ± depende do equipamento (halter 2 kg, barra 2 kg, máquina 1 placa). A revisão das cargas antigas sinaliza sessões com cerca de 1,6× ou mais da mediana das vizinhas e nunca altera nada sozinha.
- Unidade sempre explícita no histórico ("kg por halter", "kg"), e volume rotulado como volume.
- Fim do descanso: vibração por `VibrationAttributes.USAGE_ALARM` (passa pelo silencioso) e o som do PWA (`src/audio.ts`: dois bipes de 880 Hz de 0,15 s, depois um de 1320 Hz de 0,3 s, em seno). O som vira um WAV curto gerado com essas notas, tocado com `USAGE_MEDIA` como no zazen. Os dois podem ser desligados no Perfil.
- Bibliotecas entram sem pedir OK: Room, Navigation Compose, kotlinx.serialization e outras que o plano pedir.
- Distribuição por APK assinado gerado localmente, como no zazen. Custo zero.

## Onde fica

Pasta `android/` neste repo. O PWA continua na raiz e o deploy do Pages não muda.

## Fases

### 0. Spike do timer no Galaxy S23 Ultra (One UI 8.5)

- Serviço em primeiro plano `foregroundServiceType="mediaPlayback"`, canal `IMPORTANCE_LOW`.
- `AlarmManager.setExactAndAllowWhileIdle` no fim do descanso, que aciona um receiver não exportado e depois o serviço. Permissão `USE_EXACT_ALARM`.
- `PARTIAL_WAKE_LOCK` curto durante o alerta, e `POST_NOTIFICATIONS` pedido na hora de usar.
- A contagem da notificação usa `setUsesChronometer` com `setChronometerCountDown`, sem atualizar a cada segundo.
- Validar o `Notification.ProgressStyle` do Android 16 com um segmento por exercício (tamanho proporcional às séries) e a promoção a Live Update na Now Bar. Se não der, usar uma notificação comum com uma barra só.
- O fim do descanso é um heads-up colorido (`setColorized`, laranja) com +30s e Abrir.
- Fazer o teste de Doze forçado igual ao do zazen e registrar o resultado em `android/docs/SPIKE.md`.

### 1. Base

- Projeto Compose, tema da direção B:
  - cores: fundo `#101113`, cartão `#1A1C20`, destaque `#FF7A45`, tinta `#1A0D06`, texto fraco `#A8A49D` e `#8A867F`;
  - fontes: DM Sans e JetBrains Mono;
  - barra de abas flutuante em pílula.
- Room com estas tabelas:
  - `workout` (ordem = sequência circular) e `workout_item` (séries, faixa de reps ou de minutos, descanso);
  - `exercise` (grupo, tipo carga ou tempo, equipamento, "conta 2 halteres", passo);
  - `session` (data, treino, nota, início e fim);
  - `set_entry` (carga, reps, horário) e `time_entry` (min, km, local, horário).
- Ids UUID desde já, para o backend da etapa 2. O horário em cada série permite calcular a duração; os dias importados ficam sem duração.
- Domínio em Kotlin puro, com testes unitários:
  - volume;
  - carga máxima e "subiu de carga";
  - próximo treino da sequência (portar `src/plan.ts`);
  - detector de cargas suspeitas;
  - passo por equipamento.

### 2. Importar e exportar

- Importa o backup format 2 (`{app:'treino', format:2, exportedAt, logs, settings, plan, exercises, profile}`), o format 1 (logs + settings) e o objeto de logs cru. É a mesma lógica do `importBackup` em `src/storage.ts`.
- Formato dos logs: `Record<'YYYY-MM-DD', {template, sets: Record<ex, {w,r,rir?}[]>, times?: Record<ex,{min,km?,local?}[]>, cardio?, note?}>`. O `cardio` antigo vira `time_entry`.
- Exercícios com "halter" no nome entram como halteres. A carga é mantida como está.
- Antes de confirmar, o app mostra o que o arquivo traz: dias, séries, treinos, exercícios e o período.
- Exporta um format 3: o format 2 mais equipamento, horários e UUIDs.
- Teste com um backup real exportado do PWA.

### 3. Telas, na ordem da página "Telas por aba"

1. **Hoje:**
   - antes de começar e trocar o treino;
   - treino em andamento, com o cabeçalho ligado à rolagem: o título vai de 30 a 18 pt, a data some, o avatar encolhe, o cronômetro vira texto, a barra afina de 8 para 4 px e o "9/16" muda de lugar sem salto;
   - editar série registrada;
   - descanso expandido, em pílula e no fim;
   - exercício de tempo;
   - resumo.
2. **Evolução:** Dias, Dia do histórico, Exercícios e Progressão do exercício. Os gráficos são desenhados com o Canvas do Compose.
3. **Treinos:** sequência, editar treino, exercício no treino, adicionar exercício e equipamento.
4. **Perfil:** revisão das cargas antigas, som, vibração e tela ligada, backup, primeiro uso.

### 4. Timer de verdade

- O término do descanso fica salvo em SharedPreferences, e o app lê ao voltar para a frente. Nenhum contador depende da tela.
- Os botões da notificação (−15s, +15s, Pular, Registrar série) gravam direto no Room pelo receiver, sem abrir o app.
- Fora do descanso, a notificação mostra o treino, X/Y séries, a duração e a próxima série.

### 5. APK

- Build assinado local, como no zazen.
- Instalar no S23 e no celular da irmã.

### 6. Troca

- Exportar do PWA e importar no app, nos dois celulares.
- O PWA fica no ar até os dois migrarem.
- As etapas 2 (backend com login no Google Cloud) e 4 (MCP) vêm depois.
