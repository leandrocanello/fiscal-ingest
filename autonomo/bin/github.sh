#!/usr/bin/env bash
# Interação com o GitHub via `gh` (token fine-grained só deste repositório).
#   github.sh publicar ID BASE       → push + cria/atualiza o PR
#   github.sh baixar-feedback ID     → comentários do PR em planos/ID-feedback.md
#   github.sh corrigido ID           → push, comenta no PR, tira o rótulo de mudanças
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$FI_REPO"
ID="$2"; BR="auto/$ID"

case "$1" in
  publicar)
    BASE="$3"
    git push -q --force-with-lease -u origin "$BR" || exit 1
    rotulos="$ROTULO_REVISAO"; [[ "$(rm_tipo "$ID")" == "AUTO+" ]] && rotulos+=",$ROTULO_FORTE"
    titulo="$ID · $(rm_titulo "$ID") [$(rm_tipo "$ID")]"
    if gh pr view "$BR" --json number >/dev/null 2>&1; then
      gh pr edit "$BR" --body-file "$FI_ESTADO_DIR/planos/$ID-pr.md" --add-label "$rotulos" >/dev/null
    else
      gh pr create --base "$BASE" --head "$BR" --title "$titulo" \
        --body-file "$FI_ESTADO_DIR/planos/$ID-pr.md" --label "$rotulos" >/dev/null || exit 1
    fi ;;
  baixar-feedback)
    f="$FI_ESTADO_DIR/planos/$ID-feedback.md"
    num=$(gh pr view "$BR" --json number -q .number) || exit 1
    repo=$(gh repo view --json nameWithOwner -q .nameWithOwner)
    desde=$(estado_get ".corrigido_em[\"$ID\"]" || echo "1970-01-01T00:00:00Z")
    {
      echo "# Feedback do PR #$num ($ID) — comentários depois de $desde"; echo
      echo "## Comentários gerais"
      gh pr view "$BR" --json comments,reviews -q \
        "[.comments[], .reviews[]] | map(select(.body != \"\" and (.createdAt // .submittedAt) > \"$desde\")) | .[] | \"- (\(.author.login)) \(.body)\""
      echo; echo "## Comentários no código"
      gh api "repos/$repo/pulls/$num/comments" -q \
        ".[] | select(.created_at > \"$desde\") | \"- \(.path):\(.line // .original_line) — \(.body)\""
      echo; echo "## Respostas do agente"
    } > "$f" ;;
  corrigido)
    git push -q --force-with-lease origin "$BR" || exit 1
    resp=$(sed -n '/^## Respostas do agente/,$p' "$FI_ESTADO_DIR/planos/$ID-feedback.md" | tail -n +2)
    gh pr comment "$BR" --body "Correções aplicadas pelo modo autônomo.

$resp" >/dev/null
    gh pr edit "$BR" --remove-label "$ROTULO_MUDANCAS" --add-label "$ROTULO_REVISAO" >/dev/null
    estado_set ".corrigido_em[\"$ID\"]" "\"$(date -u +%FT%TZ)\"" ;;
esac
