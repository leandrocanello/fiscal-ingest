#!/usr/bin/env bash
# Instalação única, dentro do WSL (depois dos passos 1–5 de autonomo/INSTALACAO.md).
# Cria os 3 worktrees, a branch auto/estado com os modelos e os rótulos do GitHub. Idempotente.
set -euo pipefail
source "$(dirname "$0")/../config.sh"
URL="${1:-https://github.com/leandrocanello/fiscal-ingest.git}"

for cmd in git jq gh claude java flock timeout; do
  command -v "$cmd" >/dev/null || { echo "Falta o comando: $cmd"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "Rode 'gh auth login --with-token' com o token fine-grained (INSTALACAO.md passo 4)"; exit 1; }

# 1) worktree estável dos scripts (main)
[[ -d "$FI_MOTOR/.git" || -f "$FI_MOTOR/.git" ]] || git clone -q "$URL" "$FI_MOTOR"
git -C "$FI_MOTOR" checkout -q main && git -C "$FI_MOTOR" pull -q --ff-only

# 2) worktree de código (o agente trabalha aqui)
[[ -e "$FI_REPO/.git" ]] || git -C "$FI_MOTOR" worktree add -q --detach "$FI_REPO" origin/main

# 3) worktree de estado (branch auto/estado, só com a pasta estado/)
if [[ ! -e "$FI_ESTADO/.git" ]]; then
  git -C "$FI_MOTOR" fetch -q origin
  if git -C "$FI_MOTOR" show-ref -q --verify refs/remotes/origin/auto/estado; then
    git -C "$FI_MOTOR" worktree add -q "$FI_ESTADO" auto/estado
  else
    git -C "$FI_MOTOR" worktree add -q --detach "$FI_ESTADO" origin/main
    cd "$FI_ESTADO"
    git checkout -q --orphan auto/estado
    git rm -rq --cached . && find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
    mkdir -p estado/planos estado/revisao estado/logs
    cp "$FI_MOTOR"/autonomo/modelos/{ROADMAP,PARA-VOCE,DECISOES-AGENTE,PROGRESSO}.md estado/
    echo '{}' > estado/estado.json
    printf 'logs/\n' > estado/.gitignore
    printf '# Estado do modo autônomo\nVeja `estado/ROADMAP.md`, `estado/PARA-VOCE.md` e `estado/revisao/`.\nPara parar tudo: crie o arquivo `estado/PARAR`.\n' > README.md
    git add -A && git -c user.name=fiscal-autonomo -c user.email=autonomo@localhost commit -q -m "estado: início"
    git push -q -u origin auto/estado
  fi
fi

# 4) rótulos do fluxo de revisão
cd "$FI_MOTOR"
gh label create "$ROTULO_REVISAO" --color FBCA04 --description "PR do modo autônomo aguardando sua revisão" --force >/dev/null
gh label create "$ROTULO_MUDANCAS" --color D93F0B --description "Você pediu mudanças: o agente corrige na próxima janela" --force >/dev/null
gh label create "$ROTULO_FORTE" --color 5319E7 --description "Subetapa sensível (AUTO+): revise com atenção" --force >/dev/null

chmod +x "$FI_MOTOR"/autonomo/bin/*.sh
echo "Pronto. Teste manual: MAX_CHAMADAS_JANELA=1 $FI_MOTOR/autonomo/bin/janela.sh ; depois veja $FI_ESTADO_DIR/logs/"
