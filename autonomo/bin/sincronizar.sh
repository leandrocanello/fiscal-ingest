#!/usr/bin/env bash
# Lê o GitHub e atualiza o ROADMAP: merge → [x]; rótulo pedir-mudancas → [c]; PR fechado → [!].
# Depois faz rebase das branches empilhadas cuja dependência foi mergeada.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$FI_REPO"
git fetch -q origin

for id in $(rm_ids_status r) $(rm_ids_status c); do
  info=$(gh pr view "auto/$id" --json state,labels 2>/dev/null) || continue
  estado=$(jq -r .state <<<"$info")
  temmud=$(jq -r --arg l "$ROTULO_MUDANCAS" '[.labels[].name] | index($l) != null' <<<"$info")
  if [[ "$estado" == "MERGED" ]]; then rm_set "$id" x; progresso "$id: mergeado por você ✔"
  elif [[ "$estado" == "CLOSED" ]]; then rm_set "$id" '!'; progresso "$id: PR fechado sem merge → [!]"
    printf '\n### [PR FECHADO] %s — %s\nO PR foi fechado sem merge. Diga o que fazer (refazer? descartar?) e troque o status no ROADMAP.\n' "$id" "$(date +%F)" >> "$FI_ESTADO_DIR/PARA-VOCE.md"
  elif [[ "$temmud" == "true" ]]; then rm_set "$id" c
  fi
done

# Empilhadas: base auto/DEP que já foi mergeada → rebase para main
for id in $(rm_ids_status '~') $(rm_ids_status r) $(rm_ids_status c); do
  base=$(estado_get ".base[\"$id\"]" || echo main)
  [[ "$base" == auto/* ]] || continue
  dep="${base#auto/}"
  [[ "$(rm_status "$dep")" == "x" ]] || continue
  if "$AQUI/branch.sh" rebase "$id" main "$base"; then
    estado_set ".base[\"$id\"]" '"main"'
    gh pr edit "auto/$id" --base main >/dev/null 2>&1 || true
    progresso "$id: rebase para main após merge de $dep"
  else
    rm_set "$id" '!'; progresso "$id: [!] conflito no rebase para main"
  fi
done
