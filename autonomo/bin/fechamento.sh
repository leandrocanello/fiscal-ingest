#!/usr/bin/env bash
# Fechamento do dia (17:50): relatório determinístico para a sua revisão noturna.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
[[ $(date +%u) -ge 6 ]] && exit 0
"$AQUI/estado-git.sh" puxar
estado_init_dia
"$AQUI/sincronizar.sh" || true

HOJE=$(date +%F); R="$FI_ESTADO_DIR/revisao/$HOJE.md"; mkdir -p "$(dirname "$R")"
{
  echo "# Relatório $HOJE ($(date +%a))"; echo
  echo "Chamadas: $(estado_get .chamadas_hoje || echo 0) · Revertidas: $(estado_get .revertidas_hoje || echo 0)" \
       "· Diff de produção hoje: $(estado_get .diff_hoje || echo 0)/$ORCAMENTO_DIFF_DIA linhas"
  echo
  echo "## PRs para revisar (mais antigos primeiro)"
  ( cd "$FI_REPO" && gh pr list --label "$ROTULO_REVISAO" --state open \
      --json number,title,url,additions,deletions,labels,baseRefName \
      -q 'sort_by(.number) | .[] | "- [#\(.number)](\(.url)) \(.title) — +\(.additions)/-\(.deletions)" + (if .baseRefName != "main" then " · empilhado sobre `\(.baseRefName)`" else "" end) + (if ([.labels[].name] | index("revisao-forte")) then " · **revisão forte**" else "" end)' ) \
    || echo "- (não consegui consultar o GitHub)"
  echo
  echo "## Progresso de hoje"; grep -F -- "- $HOJE" "$PROGRESSO" | sed 's/^- [0-9-]* /- /' || echo "- (nada)"
  echo
  echo "## Aguardando você"
  for id in $(rm_ids_status h) $(rm_ids_status '!'); do echo "- [$(rm_status "$id")] $id — $(rm_titulo "$id")"; done
  echo "- Detalhes: \`estado/PARA-VOCE.md\`"
  echo
  echo "## Decisões do agente hoje"; grep -F "$HOJE" "$FI_ESTADO_DIR/DECISOES-AGENTE.md" | sed 's/^/- /' || echo "- (nenhuma)"
  echo
  echo "## ROADMAP (resumo)"
  for s in '~' r c '!' h; do n=$(rm_ids_status "$s" | wc -l); (( n > 0 )) && echo "- [$s] $n: $(rm_ids_status "$s" | tr '\n' ' ')"; done
  echo "- [x] $(rm_ids_status x | wc -l) aprovadas · [ ] $(rm_ids_status ' ' | wc -l) pendentes"
} > "$R"

"$AQUI/estado-git.sh" salvar "relatório $HOJE"
