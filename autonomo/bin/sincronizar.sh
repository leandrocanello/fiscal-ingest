#!/usr/bin/env bash
# Lê o GitHub e atualiza o ROADMAP: merge → [x]; rótulo pedir-mudancas → [c]; PR fechado → [!].
# Depois faz rebase das branches empilhadas cuja dependência foi mergeada.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$FI_REPO"
git fetch -q origin

# Merge que não levou tudo para a main: PR mergeado antes dos últimos commits (caso E2-S05) ou PR empilhado
# mergeado na branch da dependência em vez da main (casos E2-S08/E2-S09). Só avisa; quem recupera é você.
alerta_fora_da_main() {
  local id="$1" n
  git show-ref -q --verify "refs/remotes/origin/auto/$id" || return 0
  n=$(git rev-list --count "origin/main..origin/auto/$id")
  (( n == 0 )) && return 0
  progresso "$id: ⚠ $n commit(s) de auto/$id não estão na main"
  printf '\n### [ALERTA] %s — %s — código mergeado que não chegou à main\n%s commit(s) de `auto/%s` não estão na `main` (PR mergeado antes dos últimos commits, ou PR empilhado mergeado na branch da dependência). Ver `git log origin/main..origin/auto/%s`.\n- Resposta:\n' \
    "$id" "$(date +%F)" "$n" "$id" "$id" >> "$FI_ESTADO_DIR/PARA-VOCE.md"
}

for id in $(rm_ids_status r) $(rm_ids_status c); do
  info=$(gh pr view "auto/$id" --json state,labels 2>/dev/null) || continue
  estado=$(jq -r .state <<<"$info")
  temmud=$(jq -r --arg l "$ROTULO_MUDANCAS" '[.labels[].name] | index($l) != null' <<<"$info")
  if [[ "$estado" == "MERGED" ]]; then rm_set "$id" x; progresso "$id: mergeado por você ✔"
    alerta_fora_da_main "$id"
  elif [[ "$estado" == "CLOSED" ]]; then rm_set "$id" '!'; progresso "$id: PR fechado sem merge → [!]"
    printf '\n### [PR FECHADO] %s — %s\nO PR foi fechado sem merge. Diga o que fazer (refazer? descartar?) e troque o status no ROADMAP.\n' "$id" "$(date +%F)" >> "$FI_ESTADO_DIR/PARA-VOCE.md"
  elif [[ "$temmud" == "true" ]]; then rm_set "$id" c
  fi
done

# Em andamento cujo PR você já mergeou (ex.: abriu o PR à mão): fecha a subetapa (caso E2-S05)
for id in $(rm_ids_status '~'); do
  [[ "$(gh pr view "auto/$id" --json state -q .state 2>/dev/null)" == "MERGED" ]] || continue
  rm_set "$id" x; progresso "$id: PR já mergeado por você ✔ (estava [~])"
  alerta_fora_da_main "$id"
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
