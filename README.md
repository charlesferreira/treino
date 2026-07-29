# Treino 🏋️

PWA pessoal de acompanhamento de treino: registro de carga × reps por série, última sessão de cada exercício, timer de descanso e sinalização de progressão (double progression). Offline-first, dados só no aparelho (`localStorage`).

**App:** https://charlesferreira.github.io/treino/

## Rodar local

```bash
npm i
npm run dev
```

Para testar outro dia da semana, force a data com `?d=YYYY-MM-DD`, ex.: `http://localhost:5173/treino/?d=2026-07-27` (segunda).

## Treinos e sequência

Os treinos são editados **dentro do app** (bolinha do avatar → Meus treinos), não mais por arquivo. O programa é uma **sequência circular**: o app sugere o treino seguinte ao último que você executou. Faltou um dia? A fila não anda. Quer fazer outro? Toque no nome do treino na tela Hoje e escolha — a partir dali a rotação segue dele.

Cada exercício do treino é **por carga** (carga × reps, com RIR opcional) ou **por tempo** (minutos, com km opcional no cardio), e tem séries e descanso próprios. O seletor busca num catálogo de ~150 exercícios ([`src/exercises.json`](src/exercises.json)) e aceita qualquer coisa digitada, criando um exercício seu.

O histórico nunca é apagado por mudança de programa. Apagar um treino não apaga os dias em que ele foi feito.

### O programa de exemplo

[`src/program.json`](src/program.json) virou só a **semente** usada em duas situações: aparelho que já tinha histórico do formato antigo (migra preservando os ids dos treinos e exercícios) e importação de um backup antigo, que só tem histórico. Aparelho novo e vazio cai no onboarding: nome, iniciais viram avatar, e monta o primeiro treino.

## Backup e restauração

Os dados vivem só no aparelho, e é assim que duas pessoas usam o mesmo endereço sem se misturar. Trocar de celular exige backup. No **Perfil** (bolinha do avatar):

- **Exportar dados** baixa um `treino-backup-AAAA-MM-DD.json` com histórico, programa, exercícios e perfil.
- **Importar dados** restaura um backup (substitui os dados atuais após confirmação). Backups antigos, só com histórico, continuam válidos.

Exporte de vez em quando.

## Deploy

Push na `main` → workflow [`deploy.yml`](.github/workflows/deploy.yml) builda e publica no GitHub Pages. O `base: '/treino/'` no [`vite.config.ts`](vite.config.ts) é obrigatório para o app (e o service worker) funcionarem no Pages.

Ícones são gerados com `npm run icons` (só precisa rodar de novo se mudar o desenho em `scripts/make-icons.mjs`).
