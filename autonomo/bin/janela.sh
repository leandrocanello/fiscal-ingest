#!/usr/bin/env bash
# Uma janela de trabalho autônomo (AUTONOMO.md §11). Disparada pelo Agendador do Windows.
# Roda a partir de $FI_MOTOR (worktree estável no main).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
mkdir -p "$FI_ESTADO_DIR/logs"
LOG="$FI_ESTADO_DIR/logs/$(date +%F_%H%M).log"
exec >>"$LOG" 2>&1
exec </dev/null   # nenhum filho pode ler do terminal (evita SIGTTIN: processo pausado que nem o timeout mata)

[[ $(date +%u) -ge 6 ]] && { log "fim de semana — nada a fazer"; exit 0; }
[[ -f "$FI_ESTADO_DIR/PARAR" ]] && { log "arquivo PARAR presente — saindo"; exit 0; }
exec 9>"$HOME/.fiscal-janela.lock"; flock -n 9 || { log "outra janela em execução — saindo"; exit 0; }

FIM=$(( $(date +%s) + 5*3600 - PARAR_ANTES_DO_FIM_MIN*60 ))
log "=== janela iniciada (até $(date -d @$FIM +%H:%M)) ==="

"$AQUI/estado-git.sh" puxar
estado_init_dia
"$AQUI/sincronizar.sh" || log "aviso: sincronização com o GitHub falhou"
"$AQUI/estado-git.sh" salvar "sincroniza com o GitHub"

for ((i=1; i<=MAX_CHAMADAS_JANELA; i++)); do
  [[ $(date +%s) -ge $FIM ]] && { log "fim da janela"; break; }
  [[ -f "$FI_ESTADO_DIR/PARAR" ]] && { log "PARAR encontrado"; break; }

  read -r ACAO PAPEL MODELO ID BASE < <("$AQUI/proxima-acao.sh")
  log "--- chamada $i: $ACAO $ID ($PAPEL/$MODELO, base $BASE)"
  [[ "$ACAO" == "SAIR" ]] && { log "nada a fazer ($MODELO)"; break; }

  if [[ "$ACAO" != "PREPARAR" ]]; then
    "$AQUI/branch.sh" preparar "$ID" "$BASE" || { log "falha ao preparar a branch"; rm_set "$ID" '!'; progresso "$ID: [!] falha ao preparar branch (conflito de rebase?)"; continue; }
  fi
  [[ "$ACAO" == "CORRIGIR" ]] && "$AQUI/github.sh" baixar-feedback "$ID"

  ANTES=$(git -C "$FI_REPO" rev-parse HEAD)
  SAIDA="$FI_ESTADO_DIR/logs/$(date +%F_%H%M%S)-$ID-$ACAO.json"
  ( cd "$FI_REPO" && FI_PAPEL="$PAPEL" FI_SUB="$ID" timeout -k 30s "${TIMEOUT_CHAMADA_MIN}m" \
      claude -p "$("$AQUI/prompt.sh" "$ACAO" "$ID" "$BASE")" \
        --append-system-prompt "$(cat "$FI_MOTOR/autonomo/papeis/comum.md" "$FI_MOTOR/autonomo/papeis/$PAPEL.md")" \
        --settings "$FI_MOTOR/autonomo/claude-settings.json" \
        --add-dir "$FI_ESTADO_DIR" \
        --model "$MODELO" --max-turns "$MAX_TURNS" --output-format json \
        --dangerously-skip-permissions < /dev/null > "$SAIDA" )
  estado_inc .chamadas_hoje

  case "$("$AQUI/resultado.sh" "$SAIDA")" in
    LIMITE) log "limite de uso atingido — encerrando a janela"
            "$AQUI/verificar.sh" reverter "$ID" "$ANTES" "limite de uso no meio da chamada"
            "$AQUI/estado-git.sh" salvar "$ID: interrompido pelo limite de uso"; break ;;
    TURNOS|ERRO|TIMEOUT)
            "$AQUI/verificar.sh" falha "$ID" "$ANTES" "chamada terminou com $("$AQUI/resultado.sh" "$SAIDA")"
            "$AQUI/estado-git.sh" salvar "$ID: $ACAO falhou"; continue ;;
  esac

  "$AQUI/verificar.sh" checar "$ACAO" "$ID" "$ANTES"; rc_ver=$?
  if (( rc_ver == 2 )); then
    log "verificação travou/estourou o tempo (problema de ambiente, não do agente) — encerrando a janela"
    "$AQUI/estado-git.sh" salvar "$ID: verificação estourou o tempo"; break
  fi
  if (( rc_ver == 0 )); then
    case "$ACAO" in
      PREPARAR) rm_set "$ID" h; progresso "$ID: preparado para você (PARA-VOCE.md)" ;;
      REVISAR)
        if [[ -f "$FI_ESTADO_DIR/planos/$ID-pr.md" ]] && ! plano_tem_aberto "$FI_ESTADO_DIR/planos/$ID.md" "Desenvolvimento"; then
          if "$AQUI/github.sh" publicar "$ID" "$BASE"; then rm_set "$ID" r; progresso "$ID: PR aberto para revisão"
          else
            # Sem isto o REVISAR roda de novo a cada chamada (22× em 2026-10-26)
            rm_set "$ID" h; progresso "$ID: [h] revisão aprovada, mas abrir o PR falhou"
            printf '\n### [HUMANO] %s — %s — abrir o PR falhou\nRevisão aprovada; `github.sh publicar` falhou (ver log da janela). Abra o PR com `planos/%s-pr.md` ou corrija e troque o status para `[~]`.\n' "$ID" "$(date +%F)" "$ID" >> "$FI_ESTADO_DIR/PARA-VOCE.md"
          fi
        else
          progresso "$ID: revisor devolveu para correção"
        fi ;;
      CORRIGIR) "$AQUI/github.sh" corrigido "$ID" && rm_set "$ID" r && progresso "$ID: correções enviadas" ;;
      *) "$AQUI/branch.sh" backup "$ID" ;;
    esac
  fi
  "$AQUI/estado-git.sh" salvar "$ID: $ACAO"
done

log "=== janela encerrada ==="
