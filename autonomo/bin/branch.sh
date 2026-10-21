#!/usr/bin/env bash
# Gestão das branches de trabalho no worktree de código ($FI_REPO).
#   branch.sh preparar ID BASE   → checkout de auto/ID (cria a partir de origin/BASE se não existir)
#   branch.sh backup   ID        → push da branch (o agente nunca faz push)
#   branch.sh rebase   ID NOVA_BASE ANTIGA_BASE → rebase de empilhada depois do merge da dependência
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$FI_REPO"
git fetch -q origin

case "$1" in
  preparar)
    ID="$2"; BASE="$3"
    if git show-ref -q --verify "refs/heads/auto/$ID"; then
      git checkout -q "auto/$ID"
    elif git show-ref -q --verify "refs/remotes/origin/auto/$ID"; then
      git checkout -q -b "auto/$ID" "origin/auto/$ID"
    else
      git checkout -q -b "auto/$ID" "origin/$BASE"
    fi ;;
  backup)
    git push -q --force-with-lease -u origin "auto/$2" ;;
  rebase)
    ID="$2"; NOVA="$3"; ANTIGA="$4"
    git checkout -q "auto/$ID" || exit 1
    if ! git rebase -q --onto "origin/$NOVA" "origin/$ANTIGA" "auto/$ID"; then
      git rebase --abort; exit 1
    fi
    git push -q --force-with-lease origin "auto/$ID" ;;
esac
