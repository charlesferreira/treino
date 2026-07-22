# Treino 🏋️

PWA pessoal de acompanhamento de treino: registro de carga × reps por série, última sessão de cada exercício, timer de descanso e sinalização de progressão (double progression). Offline-first, dados só no aparelho (`localStorage`).

**App:** https://charlesferreira.github.io/treino/

## Rodar local

```bash
npm i
npm run dev
```

Para testar outro dia da semana, force a data com `?d=YYYY-MM-DD`, ex.: `http://localhost:5173/treino/?d=2026-07-27` (segunda).

## Editar o programa

O programa inteiro vive em [`src/program.json`](src/program.json): catálogo de exercícios (id estável → nome + notas) e a prescrição por dia da semana. Para trocar o programa (a cada 8–12 semanas):

1. Edite `src/program.json` — mantenha os mesmos ids para exercícios que continuam (é o id que unifica o histórico e a progressão) e suba o `version`.
2. Commit e push na `main`. O deploy é automático (GitHub Actions → GitHub Pages) e o app se atualiza sozinho no celular (`autoUpdate`).

O histórico de treinos nunca é apagado por mudança de programa.

## Backup e restauração

Os dados vivem só no aparelho. Na aba **Regras**:

- **Exportar dados** baixa um `treino-backup-AAAA-MM-DD.json`.
- **Importar dados** restaura um backup (substitui o histórico atual após confirmação).

Exporte de vez em quando.

## Deploy

Push na `main` → workflow [`deploy.yml`](.github/workflows/deploy.yml) builda e publica no GitHub Pages. O `base: '/treino/'` no [`vite.config.ts`](vite.config.ts) é obrigatório para o app (e o service worker) funcionarem no Pages.

Ícones são gerados com `npm run icons` (só precisa rodar de novo se mudar o desenho em `scripts/make-icons.mjs`).
