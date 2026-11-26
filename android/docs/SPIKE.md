# Spike: o timer de descanso toca com a tela apagada

Fase 0 do `docs/native-plan.md`. Validado em 26/11/2026 no emulador, AVD "Treino"
(Android 16 QPR2, API 36.1, imagem `google_apis_playstore` arm64). O Galaxy S23 Ultra
(One UI 8.5) ainda falta: ver "Para testar no S23" no fim.

## Resultado no emulador

| Cenário | Agendado | Disparou | Atraso |
|---|---|---|---|
| App em segundo plano, tela ligada (descanso com −15s duas vezes e +15s uma) | 04:35:41.898 | 04:35:41.903 | 5 ms |
| Tela apagada (`KEYCODE_SLEEP`, `mWakefulness=Asleep`), 15 s | 04:40:34.325 | 04:40:34.333 | 8 ms |
| Doze forçado (`force-idle`, `get deep` = `IDLE`), 15 s | 04:41:06.957 | 04:41:06.967 | 10 ms |
| Doze forçado, 90 s | 04:42:49.670 | 04:42:49.678 | 8 ms |
| Doze forçado, 15 s, build final | 04:45:04.897 | 04:45:04.908 | 11 ms |
| App atualizado no meio do descanso (`adb install -r`), alarme reposto pelo `MY_PACKAGE_REPLACED` | 04:44:34.020 | 04:44:34.055 | 35 ms (até o serviço tocar) |
| Teste de ponta a ponta de 26 nov: descanso real após a série 2 (−15s quatro vezes), app em segundo plano, `KEYCODE_SLEEP`, `force-idle` (`get deep` = `IDLE`), 60 s | 06:56:56.738 | 06:56:56.765 | 27 ms (31 ms até tocar) |

"Disparou" é o `onReceive` do receiver. O serviço começa o alerta 3 a 4 ms depois.
Em todos os casos, no mesmo segundo:

- vibração pedida e executada: `dumpsys vibrator_manager` mostra `usage: ALARM`, padrão
  `200/100/200/100/400 ms` (o mesmo do PWA), duração ~1005 ms;
- foco de áudio pedido e devolvido: `dumpsys audio` mostra `requestAudioFocus() ...
  AA=USAGE_MEDIA/CONTENT_TYPE_SONIFICATION ... req=3` (3 = `GAIN_TRANSIENT_MAY_DUCK`) e
  `abandonAudioFocus()` ~1 s depois, quando o WAV de 790 ms termina;
- o heads-up do fim aparece e o serviço sai do primeiro plano.

O app não pediu isenção de otimização de bateria e não está na whitelist do Doze.
`dumpsys alarm` mostra `exactAllowReason=policy_permission` (o `USE_EXACT_ALARM` vem concedido).

O emulador rodou com `-no-audio`: o MediaPlayer tocou o arquivo inteiro (início e fim no log,
foco concedido), mas ninguém ouviu. Ouvir o som é teste do aparelho.

## O que foi necessário

1. **Foreground service** `RestTimerService`, `foregroundServiceType="mediaPlayback"`, iniciado
   por `startForegroundService()` a partir da Activity. Notificação fixa no canal `rest`
   (`IMPORTANCE_LOW`). Permissões `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
2. **Estado em SharedPreferences** (`RestTimerStore`, arquivo `rest_timer`): término em epoch ms,
   duração total, treino, exercício, série (número e total), alvo, séries por exercício, séries
   feitas, flag `finished`, e os interruptores `sound_enabled` e `vibration_enabled` (padrão
   ligado). Escrita com `commit()`, porque receivers leem em processo novo. Nada conta tempo:
   o restante é sempre `término − agora`, e a tela lê o store quando volta à frente.
3. **Alarme exato** `setExactAndAllowWhileIdle(RTC_WAKEUP, término)` para o `RestDoneReceiver`
   (não exportado), que só faz `startForegroundService(ACTION_DONE)`. `USE_EXACT_ALARM` e
   `SCHEDULE_EXACT_ALARM maxSdkVersion=32`, como no zazen. Foi usado o relógio de parede
   (RTC) porque o término salvo é epoch; o zazen usa `ELAPSED_REALTIME`. Mudar o relógio do
   aparelho no meio de um descanso move o fim, o que é aceitável para 90 s.
4. **Alerta**: `PARTIAL_WAKE_LOCK` de 10 s, vibração `VibrationAttributes.USAGE_ALARM` (API 33+;
   abaixo, `AudioAttributes.USAGE_ALARM`), som `res/raw/rest_done.wav` por `MediaPlayer` com
   `USAGE_MEDIA`, foco `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (abaixa a música da academia em vez
   de pausar) abandonado no fim. O WAV vem de `android/tools/gen_rest_done_wav.py` (44,1 kHz,
   mono, 16 bits, 0,79 s), com as notas do `src/audio.ts`.
5. **Contagem na notificação** com `setUsesChronometer(true)`, `setChronometerCountDown(true)` e
   `setWhen(término)`. O sistema atualiza sozinho; o app não posta nada por segundo.
6. **Botões −15s, +15s, Pular** por broadcast para o `RestActionReceiver` (não exportado), que
   ajusta o store, reagenda o alarme e repõe a notificação, sem abrir o app. O −15s nunca passa
   de zero; chegando a zero, o fim toca na hora (um alarme para "agora" atrasaria uns 5 s,
   como visto no zazen). Pular cancela o alarme, limpa o store e para o serviço.
7. **+30s no heads-up** reinicia o descanso com 30 s e sobe o serviço de novo por
   `startForegroundService` a partir do receiver. Funcionou: tocar numa ação de notificação dá
   a isenção temporária para iniciar FGS em segundo plano.
8. **`POST_NOTIFICATIONS`** pedido ao iniciar o primeiro descanso. No teste ele foi concedido
   por `pm grant`.
9. **Reboot e atualização do app** apagam o alarme e matam o serviço. O `RestRestoreReceiver`
   (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) só repõe o alarme (Android 15+ proíbe FGS
   `mediaPlayback` a partir do boot, como o zazen descobriu); o alarme sobe o serviço. Um
   descanso que acabou há mais de 60 s é marcado como terminado sem alerta. A Activity também
   chama `RestTimer.resumeIfActive()` no `onResume`, que repõe serviço e alarme se o store disser
   que há descanso rodando.

## ProgressStyle e Live Update (API 36.1 no emulador)

- `Notification.ProgressStyle` funcionou: um segmento por exercício, comprimento = número de
  séries (amostra 3, 3, 2, 2, 2, 2, 2 = 16 séries), progresso = séries feitas (9). Na cortina
  aparecem as 7 barras com a parte feita em laranja cheio e o resto em laranja claro, igual ao
  `android-shade`/`android-lock`. Não foram usados pontos (`Point`): o design não tem.
- Promoção a Live Update: `Builder.setRequestPromotedOngoing(true)` é **API 36.1**; na 36.0 existe
  só o extra `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` (`"android.requestPromotedOngoing"`), que
  o código grava direto quando `SDK_INT_FULL < BAKLAVA_1`. A permissão
  `POST_PROMOTED_NOTIFICATIONS` existe (36.1) e foi declarada; é normal, concedida na instalação.
- No emulador: `canPostPromotedNotifications()=true`, `hasPromotableCharacteristics()=true`, e o
  `dumpsys notification` mostra `flags=...|FOREGROUND_SERVICE|PROMOTED_ONGOING`. A barra de status
  mostrou o chip com o haltere e a contagem regressiva (`01:09`) fora da cortina. É o Live Update
  do AOSP; a Now Bar da One UI é outra implementação e precisa do S23.
- Em API < 36 o código cai para `setProgress(total, feitas, false)`, uma barra só (não testado:
  a única imagem é 36.1).
- O texto "16 kg × 10" não tem linha própria: com ProgressStyle não dá para usar BigText junto.
  Ficou no texto: "Rosca martelo, série 2 de 2, 16 kg × 10", com "Superior B" no subtexto do
  cabeçalho.

## setColorized no heads-up do fim

**Não funcionou no emulador.** O heads-up aparece (canal `rest_done`, `IMPORTANCE_HIGH`, sem som
de canal, `CATEGORY_ALARM`, ações +30s e Abrir, título "Hora da série 2", texto "Rosca martelo
16 kg × 10"), mas com o fundo padrão do sistema, não laranja.

- Como notificação comum: o Android só pinta `setColorized` em notificação de FGS, MediaStyle,
  CallStyle ou de app com permissão de sistema. Esperado.
- Tentativa 2: postar o heads-up como a notificação do próprio FGS (`startForeground` com o id do
  fim) e depois `stopForeground(DETACH)`. Continuou sem cor no heads-up, e a
  notificação ficou com `NO_CLEAR` depois do detach (não dava para dispensar). Revertido.
- Ficou o heads-up comum, com `setColor(#FF7A45)` (ícone e ações em laranja) e `setColorized(true)`
  mantido no código, para o caso de a One UI respeitar. Conferir no S23. Se lá também não pintar,
  o laranja inteiro do `android-done` não sai por notificação; a alternativa seria um
  `fullScreenIntent` com uma Activity própria na tela bloqueada, que exige a permissão
  `USE_FULL_SCREEN_INTENT` (restrita a apps de alarme e chamada na Play, irrelevante para APK).

## Observações para as próximas fases

- A tela de spike (hoje `debug/TimerSpikeScreen.kt`) atualiza o texto a cada 250 ms só para a tela; o timer não
  depende dela. Ela some na Fase 3.
- O botão "Registrar série" (`RestNotifications.logSetAction()`, `ACTION_LOG_SET`) ficou na
  notificação "entre séries" da Fase 4 e grava no Room pelo `RestTimer.setLogHandler`. Durante o
  descanso ele não aparece: a notificação mostra no máximo três ações (−15s, +15s, Pular).
- O ícone do app ainda é o padrão do Android (a notificação usa um haltere vetorial em
  `ic_notification`). Ícone de verdade na Fase 1/3.
- `am start-foreground-service` via adb não serve: serviço e receivers não são exportados.
  Testar pelos botões.
- `uiautomator dump` falha com "could not get idle state" enquanto a notificação com cronômetro
  está visível na cortina; usar coordenadas do screenshot.

## Como reproduzir o teste de Doze

```bash
cd android && ./gradlew :app:assembleDebug
~/Library/Android/sdk/emulator/emulator -avd Treino -no-snapshot-save   # em segundo plano
adb wait-for-device && adb shell getprop sys.boot_completed             # 1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant dev.charlesferreira.treino android.permission.POST_NOTIFICATIONS
adb shell am start -n dev.charlesferreira.treino/.MainActivity
# Desde a Fase 1 a tela do spike fica em Perfil (avatar) > Debug > "Timer (spike da Fase 0)",
# só no build debug. Os botões "Descanso 15s" e "Descanso 90s" estão no meio da tela.
adb shell input keyevent KEYCODE_SLEEP
adb shell dumpsys deviceidle force-idle      # "Now forced in to deep idle mode"
adb shell dumpsys deviceidle get deep        # IDLE
adb logcat -v time -s Treino/Timer           # "alarm fired at ..., scheduled ..., delay N ms"
adb shell dumpsys vibrator_manager | grep treino
adb shell dumpsys audio | grep treino
adb shell dumpsys deviceidle unforce
```

## Para testar no S23

Instalar o APK de debug (`adb install -r`) e fazer, nesta ordem:

1. **Now Bar / Live Update (One UI 8.5).** Iniciar "Descanso 90s" e bloquear. Conferir se o
   descanso aparece na Now Bar da tela bloqueada e como chip na barra de status, com a contagem
   andando sozinha. Abrir a cortina: os 7 segmentos com 9 de 16 séries, ações −15s, +15s, Pular.
   Se não promover: `adb shell dumpsys notification --noredact | grep -A2 treino` e ver se tem
   `PROMOTED_ONGOING`; ver também em Configurações > Notificações se a One UI tem um interruptor
   de "Live notifications" para o app.
2. **Botões na tela bloqueada.** −15s, +15s e Pular sem desbloquear. Ver se a contagem pula na
   hora e se o fim toca no horário novo.
3. **Volume de mídia zerado.** Baixar a mídia a 0 e deixar um descanso de 15 s acabar. Esperado:
   vibra, sem som (o som é `USAGE_MEDIA`). Decidir se a tela avisa quando a mídia está em 0.
4. **Vibração no silencioso.** Modo silencioso e depois modo vibrar, mídia em volume normal.
   Esperado: vibra nos dois (`USAGE_ALARM` passa pelo silencioso). Ver também com "Não perturbe"
   ligado; alarme costuma passar, mas a One UI pode ter regra própria.
5. **Música da academia.** Tocar música no Spotify/YouTube Music e deixar o descanso acabar.
   Esperado: a música abaixa durante o bipe (duck) e volta, sem pausar.
6. **Heads-up colorido.** Ver se o heads-up do fim sai laranja na One UI e se aparece em cima da
   tela bloqueada. Anotar aqui o resultado.
7. **Doze natural de verdade.** Iniciar um descanso de 90 s, bloquear, deixar o telefone parado
   na mesa sem cabo por 30 min ou mais antes (para entrar em Doze profundo de verdade; conferir
   `adb shell dumpsys power | grep mWakefulness` = `Dozing` e `dumpsys deviceidle get deep`) e
   registrar agendado/disparou/atraso no logcat (`-s Treino/Timer`), como na tabela acima.
8. **App sem abrir por dias.** O risco da Samsung visto no zazen (app em "sono profundo" cancela
   alarmes). Não reproduzível no teste; ficar de olho no uso real.
9. **Notificação entre séries (Fase 4).** Começar um treino, registrar uma série e pular o descanso.
   Na cortina e na tela bloqueada deve aparecer "Superior B, 1/16" com o cronômetro subindo e
   "A seguir: ...". Ver se a One UI a promove (Now Bar) mesmo sem serviço em primeiro plano, e se
   ela continua lá depois de 30 min com a tela apagada (a One UI pode limpar notificações ongoing de
   apps em segundo plano).
10. **"Registrar série" com o app fechado.** Tirar o app dos recentes, tocar "Registrar série" na
    tela bloqueada sem desbloquear. Esperado: a série aparece no app, o descanso seguinte começa e a
    contagem aparece. Se a One UI pedir desbloqueio para a ação, anotar aqui.
11. **Fim do descanso com o app aberto.** Deixar um descanso acabar com o Hoje na tela: toca e vibra,
    aparece "Hora da série N" no app e nenhum heads-up por cima.
