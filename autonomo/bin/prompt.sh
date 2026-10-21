#!/usr/bin/env bash
# Monta o prompt de uma chamada:  prompt.sh ACAO ID BASE
set -uo pipefail
source "$(dirname "$0")/lib.sh"
ACAO="$1"; ID="$2"; BASE="${3:-main}"
PLANO="$FI_ESTADO_DIR/planos/$ID.md"
MODELO_PLANO="$FI_MOTOR/autonomo/modelos/planos/MODELO.md"

cat <<EOF
# Chamada autônoma — $ACAO · $ID

Subetapa: **$ID** — $(rm_titulo "$ID")
Tipo: $(rm_tipo "$ID") · Dependências: $(rm_deps "$ID" | sed 's/^$/-/')
Branch de trabalho: auto/$ID (base: $BASE) — já está em checkout em $FI_REPO
Plano: $PLANO
Pasta de estado (fora do repositório de código): $FI_ESTADO_DIR

EOF

case "$ACAO" in
  PLANEJAR) cat <<EOF
Tarefa: criar o plano da subetapa em $PLANO, seguindo exatamente o formato de $MODELO_PLANO.
- Leia o CLAUDE.md (já carregado) e escolha as seções que importam; liste-as em "Ler:".
- Passos de Desenvolvimento pequenos (1 commit cada), cada um começando por "Teste:".
- Se a subetapa exigir uma decisão que os docs não tomam, escreva o item em $FI_ESTADO_DIR/PARA-VOCE.md
  e coloque como primeiro passo "- [!] aguardando decisão PARA-VOCE #N". Não invente a decisão.
EOF
  ;;
  DESENVOLVER) cat <<EOF
Tarefa: implementar SOMENTE o primeiro passo "- [ ]" da seção "## Desenvolvimento" de $PLANO, em TDD,
marcar [x] no plano e fazer 1 commit no repositório de código. Regras completas no seu papel.
EOF
  ;;
  VERIFICAR) cat <<EOF
Tarefa: executar a seção "## Verificação" de $PLANO: testes de propriedade/contrato/caos pedidos,
suíte inteira verde, checagem das invariantes listadas. Marque cada item [x] e faça commit.
EOF
  ;;
  REVISAR) cat <<EOF
Tarefa: revisar o diff da subetapa (git diff origin/$BASE...HEAD) contra o CLAUDE.md e docs/ARCHITECTURE.md.
1. Escreva o parecer na seção "## Parecer do revisor" de $PLANO.
2. Escreva a descrição do PR em $FI_ESTADO_DIR/planos/$ID-pr.md (modelo: $FI_MOTOR/autonomo/modelos/PR.md),
   incluindo "Olhar com atenção" com arquivo:linha e motivo.
3. Se houver desvio grave das invariantes, NÃO escreva o PR: adicione passos "- [ ] Corrigir: ..." na seção
   Desenvolvimento do plano (o ciclo voltará para o desenvolvedor).
EOF
  ;;
  CORRIGIR) cat <<EOF
Tarefa: o autor pediu mudanças no PR. Os comentários estão em $FI_ESTADO_DIR/planos/$ID-feedback.md.
Trate cada comentário com TDD (teste que reproduz → correção), 1 commit por comentário, e responda
item a item ao final do arquivo de feedback (seção "## Respostas do agente").
Se discordar de um pedido, NÃO aplique: explique na resposta e registre em PARA-VOCE.md.
EOF
  ;;
  PREPARAR) cat <<EOF
Esta subetapa é do tipo $(rm_tipo "$ID") e depende do autor. Não implemente nada.
Escreva em $FI_ESTADO_DIR/PARA-VOCE.md um item novo com: o que precisa ser feito/decidido, opções com prós e
contras, sua recomendação, e um roteiro passo a passo (comandos, links oficiais) para o autor executar.
EOF
  ;;
esac
