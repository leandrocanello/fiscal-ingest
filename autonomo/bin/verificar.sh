#!/usr/bin/env bash
# Verificação depois de cada chamada (AUTONOMO.md §7.2). Quem decide "deu certo" é este script.
#   verificar.sh checar   ACAO ID ANTES   → exit 0 se aprovado; senão reverte e conta falha
#   verificar.sh falha    ID ANTES MOTIVO → reverte e conta falha
#   verificar.sh reverter ID ANTES MOTIVO → reverte sem contar falha
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$FI_REPO"

reverter() { # ID ANTES MOTIVO
  git reset -q --hard "$2"; git clean -qfd
  git -C "$FI_ESTADO" checkout -q -- estado/planos 2>/dev/null || true   # desfaz checkboxes marcados
  git -C "$FI_ESTADO" clean -qfd -- estado/planos 2>/dev/null || true     # e planos parciais
  progresso "$1: revertido — $3"; estado_inc .revertidas_hoje
}
falha() { # ID ANTES MOTIVO
  reverter "$@"
  local n; n=$(( $(estado_get ".falhas[\"$1\"]" || echo 0) + 1 ))
  estado_set ".falhas[\"$1\"]" "$n"
  if (( n >= MAX_FALHAS )); then
    rm_set "$1" '!'
    cat >> "$FI_ESTADO_DIR/PARA-VOCE.md" <<EOF

### [BLOQUEIO] $1 — $(date +%F) — $n falhas seguidas
Último motivo: $3
Logs: \`estado/logs/\` (arquivos *-$1-*.json). Para destravar: ajuste o plano \`estado/planos/$1.md\`
ou responda aqui, e troque o status no ROADMAP de \`[!]\` para \`[~]\`.
EOF
    progresso "$1: [!] bloqueado após $n falhas"
  fi
}

modo="$1"; shift
case "$modo" in
  reverter) reverter "$@"; exit 0 ;;
  falha)    falha "$@"; exit 0 ;;
esac

ACAO="$1"; ID="$2"; ANTES="$3"

# 1) Arquivos protegidos intactos (commits e área de trabalho)
mudados=$( { git diff --name-only "$ANTES" HEAD; git status --porcelain | cut -c4-; } | sort -u )
for p in "${PROTEGIDOS[@]}"; do
  if grep -qE "^${p}(/|$)" <<<"$mudados"; then falha "$ID" "$ANTES" "alterou arquivo protegido ($p)"; exit 1; fi
done

case "$ACAO" in
  PLANEJAR|REVISAR|PREPARAR)
    # Papéis que não mexem no código
    if [[ -n "$mudados" ]]; then falha "$ID" "$ANTES" "$ACAO alterou o repositório de código"; exit 1; fi ;;
  DESENVOLVER|VERIFICAR|CORRIGIR)
    if [[ -n "$(git status --porcelain)" ]]; then falha "$ID" "$ANTES" "terminou com alterações sem commit"; exit 1; fi
    if [[ "$(git rev-parse HEAD)" == "$ANTES" && "$ACAO" == "DESENVOLVER" ]]; then
      falha "$ID" "$ANTES" "nenhum commit feito"; exit 1; fi
    if ! timeout 30m bash -c "$COMANDO_TESTES" > "$FI_ESTADO_DIR/logs/ultimo-teste.log" 2>&1; then
      falha "$ID" "$ANTES" "suíte vermelha (ver logs/ultimo-teste.log)"; exit 1; fi
    linhas=$(git diff --numstat "$ANTES" HEAD -- '*/src/main/*' | awk '{s+=$1+$2} END {print s+0}')
    estado_inc .diff_hoje "$linhas" ;;
esac

estado_set ".falhas[\"$ID\"]" 0
progresso "$ID: $ACAO ok"
exit 0
