#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit|NotebookEdit). Saída 2 = bloqueia; stderr volta para o agente.
# Verificado: hooks disparam mesmo com --dangerously-skip-permissions e recebem o caminho ABSOLUTO.
f=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')
[[ -z "$f" ]] && exit 0
bloquear() { echo "BLOQUEADO pelo modo autônomo: $1. Se for realmente necessário, registre em $FI_ESTADO_DIR/PARA-VOCE.md e termine." >&2; exit 2; }

rel_repo="${f#"$FI_REPO"/}"
# Protegidos no repositório de código
case "$rel_repo" in
  CLAUDE.md|AUTONOMO.md|docs/ARCHITECTURE.md|docs/DECISIONS.md|.claude/*|autonomo/*|.github/*)
    bloquear "$rel_repo é somente leitura" ;;
esac
# Estado: só o script mexe no ROADMAP e no estado.json
case "$f" in
  "$FI_ESTADO_DIR/ROADMAP.md"|"$FI_ESTADO_DIR/estado.json"|"$FI_ESTADO_DIR"/revisao/*|"$FI_ESTADO_DIR"/logs/*)
    bloquear "$(basename "$f") é mantido pelo script" ;;
esac
# Nada fora das duas pastas de trabalho
if [[ "$f" != "$FI_REPO"/* && "$f" != "$FI_ESTADO_DIR"/* ]]; then bloquear "fora das pastas do projeto ($f)"; fi
# Planejador/revisor/preparar: só arquivos de estado (+ rascunho de fontes oficiais)
if [[ "$FI_PAPEL" == "planejador" || "$FI_PAPEL" == "revisor" ]]; then
  [[ "$f" == "$FI_ESTADO_DIR"/* ]] && exit 0
  bloquear "o papel $FI_PAPEL não edita o código"
fi
# Desenvolvedor/testador: plano só da própria subetapa
if [[ "$f" == "$FI_ESTADO_DIR"/planos/* && "$(basename "$f")" != "$FI_SUB"* ]]; then
  bloquear "só o plano de $FI_SUB pode ser editado"
fi
exit 0
