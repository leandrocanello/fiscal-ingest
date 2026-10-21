#!/usr/bin/env bash
# Funções compartilhadas pelos scripts do modo autônomo.
# Formato de linha do ROADMAP:  - [S] ID | TIPO | deps: A,B | título
# Status: ' ' pendente · '~' andamento · 'r' revisão · 'c' correção pedida · 'x' aprovado
#         '!' bloqueado · 'h' aguardando você (DECISAO/HUMANO preparado)

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../config.sh
source "$AQUI/../config.sh"

ROADMAP="$FI_ESTADO_DIR/ROADMAP.md"
ESTADO_JSON="$FI_ESTADO_DIR/estado.json"
PROGRESSO="$FI_ESTADO_DIR/PROGRESSO.md"

log() { echo "[$(date '+%F %T')] $*"; }

# ---------- ROADMAP ----------
_rm_linha() { grep -E "^- \[.\] $1 \|" "$ROADMAP" | head -1; }
rm_status() { _rm_linha "$1" | sed -E 's/^- \[(.)\].*/\1/'; }
rm_tipo()   { _rm_linha "$1" | awk -F'|' '{gsub(/ /,"",$2); print $2}'; }
rm_deps()   { _rm_linha "$1" | awk -F'|' '{sub(/^ *deps: */,"",$3); gsub(/ /,"",$3); if($3=="-") $3=""; gsub(/,/," ",$3); print $3}'; }
rm_titulo() { _rm_linha "$1" | awk -F'|' '{sub(/^ */,"",$4); print $4}'; }
rm_ids()    { grep -E '^- \[.\] [A-Z0-9-]+ \|' "$ROADMAP" | sed -E 's/^- \[.\] ([A-Z0-9-]+) .*/\1/'; }
rm_ids_status() { grep -E "^- \[$1\] [A-Z0-9-]+ \|" "$ROADMAP" | sed -E 's/^- \[.\] ([A-Z0-9-]+) .*/\1/'; }

rm_set() { # rm_set ID STATUS
  local id="$1" st="$2"
  sed -i -E "s/^- \[.\] ($id) \|/- [$st] \1 |/" "$ROADMAP"
}

# Expande "E3-S02..07" em "E3-S02 E3-S03 ... E3-S07"
expandir_deps() {
  local d out=""
  for d in $1; do
    if [[ "$d" =~ ^(E[0-9]+-S)([0-9]+)\.\.([0-9]+)$ ]]; then
      local p="${BASH_REMATCH[1]}" a=$((10#${BASH_REMATCH[2]})) b=$((10#${BASH_REMATCH[3]})) i
      for ((i=a; i<=b; i++)); do out+="$(printf '%s%02d ' "$p" "$i")"; done
    else out+="$d "; fi
  done
  echo "$out"
}

fila_revisao() { echo $(( $(rm_ids_status r | wc -l) + $(rm_ids_status c | wc -l) )); }

# Uma subetapa é elegível se todas as deps estão [x], ou se no máximo UMA está em [r]/[c]
# (empilhamento) e a fila de revisão está abaixo do limite. Imprime a branch base.
base_se_elegivel() {
  local id="$1" d st nao_ok=0 base="main"
  for d in $(expandir_deps "$(rm_deps "$id")"); do
    st="$(rm_status "$d")"
    case "$st" in
      x) ;;
      r|c) nao_ok=$((nao_ok+1)); base="auto/$d" ;;
      *) return 1 ;;
    esac
  done
  (( nao_ok > 1 )) && return 1
  (( nao_ok == 1 && $(fila_revisao) >= FILA_REVISAO_MAX )) && return 1
  echo "$base"
}

# ---------- estado.json ----------
estado_init_dia() {
  [[ -f "$ESTADO_JSON" ]] || echo '{}' > "$ESTADO_JSON"
  local hoje; hoje="$(date +%F)"
  if [[ "$(jq -r '.data // ""' "$ESTADO_JSON")" != "$hoje" ]]; then
    jq --arg d "$hoje" '.data=$d | .diff_hoje=0 | .chamadas_hoje=0 | .revertidas_hoje=0' \
      "$ESTADO_JSON" > "$ESTADO_JSON.tmp" && mv "$ESTADO_JSON.tmp" "$ESTADO_JSON"
  fi
}
estado_get() { jq -r "$1 // empty" "$ESTADO_JSON"; }
estado_set() { # estado_set '.campo' valor_json
  jq "$1 = $2" "$ESTADO_JSON" > "$ESTADO_JSON.tmp" && mv "$ESTADO_JSON.tmp" "$ESTADO_JSON"
}
estado_inc() { estado_set "$1" "($(estado_get "$1" || echo 0) + ${2:-1})"; }

progresso() { echo "- $(date '+%F %H:%M') · $*" >> "$PROGRESSO"; }

# Plano: tem passo aberto na seção dada?
plano_tem_aberto() { # plano_tem_aberto ARQ "Desenvolvimento"
  awk -v sec="## $2" '
    $0 ~ "^## " { dentro = ($0 == sec) }
    dentro && /^- \[ \]/ { achou=1 }
    END { exit achou ? 0 : 1 }' "$1"
}
