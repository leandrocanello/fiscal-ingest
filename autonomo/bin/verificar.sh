#!/usr/bin/env bash
# Verificação depois de cada chamada (AUTONOMO.md §7.2). Quem decide "deu certo" é este script.
#   verificar.sh checar   ACAO ID ANTES   → exit 0 aprovado · 1 reprovado (revertido) · 2 ambiente travou (mantido)
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
      # Sem commit só é aceito se o passo foi fechado no plano (ex.: "conferir X" que já estava certo).
      # O estado é salvo (commit) depois de cada chamada, então comparar com o HEAD da branch de estado
      # mostra exatamente o que esta chamada mudou no plano.
      fechados_antes=$(git -C "$FI_ESTADO" show "HEAD:estado/planos/$ID.md" 2>/dev/null | grep -c '^- \[x\]')
      fechados_agora=$(grep -c '^- \[x\]' "$FI_ESTADO_DIR/planos/$ID.md" 2>/dev/null)
      if (( ${fechados_agora:-0} > ${fechados_antes:-0} )); then
        progresso "$ID: passo concluído sem alteração de código (aceito)"
      else
        falha "$ID" "$ANTES" "nenhum commit feito e nenhum passo fechado no plano"; exit 1
      fi
    fi
    # Para daemons que o agente tenha deixado (seguram locks) e roda a suíte sem terminal, com kill garantido.
    ./gradlew --stop -q </dev/null >/dev/null 2>&1 || true
    pkill -f GradleDaemon 2>/dev/null || true
    timeout -k 30s "${TIMEOUT_TESTES_MIN}m" bash -c "$COMANDO_TESTES" </dev/null > "$FI_ESTADO_DIR/logs/ultimo-teste.log" 2>&1
    rc_testes=$?
    pkill -f GradleDaemon 2>/dev/null || true   # nada fica pendurado segurando lock
    if (( rc_testes == 124 || rc_testes == 137 )); then
      # Estourou o tempo: problema de ambiente. NÃO reverte o trabalho do agente nem conta falha.
      progresso "$ID: verificação estourou ${TIMEOUT_TESTES_MIN} min — trabalho mantido, janela encerrada (ver logs/ultimo-teste.log)"
      exit 2
    elif (( rc_testes != 0 )); then
      falha "$ID" "$ANTES" "suíte vermelha (ver logs/ultimo-teste.log)"; exit 1
    fi
    linhas=$(git diff --numstat "$ANTES" HEAD -- '*/src/main/*' | awk '{s+=$1+$2} END {print s+0}')
    estado_inc .diff_hoje "$linhas" ;;
esac

estado_set ".falhas[\"$ID\"]" 0
progresso "$ID: $ACAO ok"
exit 0
