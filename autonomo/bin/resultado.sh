#!/usr/bin/env bash
# Classifica a saída JSON de `claude -p --output-format json`.
# Imprime: OK | TURNOS | LIMITE | ERRO | TIMEOUT
# Verificado (Claude Code 2.1.306): turnos esgotados → subtype "error_max_turns", is_error true.
# [VERIFICAR] o texto exato quando o LIMITE DE USO da assinatura é atingido; a regex abaixo é uma aposta.
f="$1"
[[ -s "$f" ]] || { echo TIMEOUT; exit 0; }
jq -e . "$f" >/dev/null 2>&1 || { echo ERRO; exit 0; }
sub=$(jq -r '.subtype // ""' "$f"); err=$(jq -r '.is_error // false' "$f")
txt=$(jq -r '[.result, (.errors // [] | tostring), .terminal_reason] | map(tostring) | join(" ")' "$f")
if [[ "$sub" == "error_max_turns" ]]; then echo TURNOS
elif [[ "$err" == "true" ]] && grep -qiE 'usage limit|limit reached|rate.?limit|out of (extra )?usage|resets? (at|in)|limite de uso' <<<"$txt"; then echo LIMITE
elif [[ "$err" == "true" && "$(jq -r '.num_turns // 0' "$f")" -le 1 ]]; then
  # Erro antes de o agente trabalhar (cota/API): trata como LIMITE — encerra a janela sem contar falha
  echo LIMITE
elif [[ "$err" == "true" ]]; then echo ERRO
else echo OK; fi
