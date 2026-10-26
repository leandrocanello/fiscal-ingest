#!/usr/bin/env bash
# Decide a próxima ação de forma DETERMINÍSTICA (AUTONOMO.md §7.1).
# Saída (uma linha):  ACAO PAPEL MODELO ID BASE
#   ACAO ∈ CORRIGIR | PLANEJAR | DESENVOLVER | VERIFICAR | REVISAR | PREPARAR | SAIR
# Pode alterar o ROADMAP (marcar [~] ao iniciar uma subetapa).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
estado_init_dia

modelo_dev() { [[ "$(rm_tipo "$1")" == "AUTO+" ]] && echo "$MODELO_FORTE" || echo "$MODELO_PADRAO"; }
base_de() { estado_get ".base[\"$1\"]" || echo main; }

# 1) Pedidos de mudança têm prioridade (mesmo com orçamento estourado)
for id in $(rm_ids_status c); do
  echo "CORRIGIR desenvolvedor $(modelo_dev "$id") $id $(base_de "$id")"; exit 0
done

# 2) Orçamento diário de revisão
if (( $(estado_get .diff_hoje || echo 0) >= ORCAMENTO_DIFF_DIA )); then
  echo "SAIR - - orcamento -"; exit 0
fi

# 3) Subetapa em andamento
atual="$(rm_ids_status '~' | head -1)"
# Plano com passo "- [!]" está esperando decisão sua: vira [h] em vez de gastar chamadas (caso E1-S06)
if [[ -n "$atual" ]] && grep -qE '^\s*- \[!\]' "$FI_ESTADO_DIR/planos/$atual.md" 2>/dev/null; then
  rm_set "$atual" h; progresso "$atual: plano aguarda decisão (passo [!]) → [h]"
  atual="$(rm_ids_status '~' | head -1)"
fi
if [[ -n "$atual" ]]; then
  plano="$FI_ESTADO_DIR/planos/$atual.md"
  base="$(base_de "$atual")"
  if [[ ! -f "$plano" ]]; then
    echo "PLANEJAR planejador $MODELO_FORTE $atual $base"
  elif plano_tem_aberto "$plano" "Desenvolvimento"; then
    m="$(grep -m1 -E '^Modelo dev:' "$plano" | awk '{print $3}')"
    echo "DESENVOLVER desenvolvedor ${m:-$(modelo_dev "$atual")} $atual $base"
  elif plano_tem_aberto "$plano" "Verificação"; then
    echo "VERIFICAR testador $MODELO_PADRAO $atual $base"
  else
    echo "REVISAR revisor $MODELO_FORTE $atual $base"
  fi
  exit 0
fi

# 4) Próxima subetapa elegível, na ordem do ROADMAP.
#    Com a fila de revisão cheia, não começa código novo (só prepara itens para você).
fila_cheia=0; (( $(fila_revisao) >= FILA_REVISAO_MAX )) && fila_cheia=1
for id in $(rm_ids_status ' '); do
  tipo="$(rm_tipo "$id")"
  base="$(base_se_elegivel "$id")" || continue
  case "$tipo" in
    AUTO|AUTO+|RASCUNHO)
      (( fila_cheia )) && continue
      rm_set "$id" '~'
      estado_set ".base[\"$id\"]" "\"$base\""
      echo "PLANEJAR planejador $MODELO_FORTE $id $base"; exit 0 ;;
    DECISAO|HUMANO)
      echo "PREPARAR planejador $MODELO_FORTE $id -"; exit 0 ;;
  esac
done

(( fila_cheia )) && { echo "SAIR - - fila-de-revisao-cheia -"; exit 0; }
echo "SAIR - - ocioso -"
