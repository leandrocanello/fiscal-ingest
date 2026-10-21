#!/usr/bin/env bash
# Sincroniza a branch auto/estado (ROADMAP, planos, relatórios). Só o SCRIPT faz commit/push aqui.
#   estado-git.sh puxar | salvar "mensagem"
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$FI_ESTADO"
case "$1" in
  puxar)  git pull -q --rebase --autostash origin auto/estado ;;
  salvar)
    git add -A estado
    git diff --cached --quiet && exit 0
    git -c user.name="fiscal-autonomo" -c user.email="autonomo@localhost" commit -q -m "estado: $2"
    for t in 1 2 3; do
      git push -q origin auto/estado && exit 0
      git pull -q --rebase origin auto/estado   # você pode ter editado PARA-VOCE/ROADMAP no GitHub
    done
    log "aviso: não consegui enviar o estado" ;;
esac
