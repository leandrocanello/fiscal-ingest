#!/usr/bin/env bash
# PreToolUse (Bash). Bloqueia comandos que só o script pode executar ou que são perigosos.
c=$(jq -r '.tool_input.command // ""')
padrao='git +push|git +(checkout|switch) +(main|-)|git +(reset +--hard|rebase|merge|branch +-[dD]|tag)|git +-C|(^|[;&| ])gh +|rm +-rf +(/|~|\$HOME|\.\.)|curl[^|]*\| *(ba)?sh|wget[^|]*\| *(ba)?sh|sudo|chmod +-R|ssh |scp '
if grep -qE "$padrao" <<<"$c"; then
  echo "BLOQUEADO pelo modo autônomo: comando não permitido ($c). Push, troca de branch, rebase e GitHub são feitos pelo script." >&2
  exit 2
fi
# Edição de protegidos via shell
if grep -qE '(sed +-i|>|tee)[^|;&]*(CLAUDE\.md|AUTONOMO\.md|ARCHITECTURE\.md|DECISIONS\.md|ROADMAP\.md|estado\.json|autonomo/|\.claude/)' <<<"$c"; then
  echo "BLOQUEADO: edição de arquivo protegido via shell." >&2; exit 2
fi
exit 0
