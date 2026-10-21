#!/usr/bin/env bash
# Configuração do modo autônomo. Ajuste aqui (por PR) depois de medir a semana 1.

# Caminhos dentro do WSL (3 worktrees do mesmo repositório)
export FI_MOTOR="${FI_MOTOR:-$HOME/fiscal-motor}"        # main: de onde os scripts rodam (estável)
export FI_REPO="${FI_REPO:-$HOME/fiscal-ingest}"          # worktree de código (branches auto/*)
export FI_ESTADO="${FI_ESTADO:-$HOME/fiscal-estado}"      # worktree da branch auto/estado
export FI_ESTADO_DIR="$FI_ESTADO/estado"                  # pasta com ROADMAP, planos, relatórios...
export CLAUDE_CONFIG_DIR="${CLAUDE_CONFIG_DIR:-$HOME/.claude-pessoal}"

# Limites
MAX_CHAMADAS_JANELA=${MAX_CHAMADAS_JANELA:-20}        # chamadas ao Claude por janela
TIMEOUT_CHAMADA_MIN=${TIMEOUT_CHAMADA_MIN:-45}        # minutos por chamada
MAX_TURNS=${MAX_TURNS:-60}                  # turnos por chamada
ORCAMENTO_DIFF_DIA=${ORCAMENTO_DIFF_DIA:-800}        # linhas (adicionadas+removidas) em src/main por dia
FILA_REVISAO_MAX=${FILA_REVISAO_MAX:-2}            # subetapas em [r]/[c] ao mesmo tempo
PARAR_ANTES_DO_FIM_MIN=${PARAR_ANTES_DO_FIM_MIN:-15}     # encerra a janela N min antes das 5h
MAX_FALHAS=${MAX_FALHAS:-3}                  # falhas no mesmo passo antes de marcar [!]

# Modelos por papel (AUTO+ usa o modelo "forte" também no desenvolvimento)
MODELO_FORTE="opus"
MODELO_PADRAO="sonnet"

# Comando de testes (suíte inteira)
COMANDO_TESTES="${COMANDO_TESTES:-./gradlew test --no-daemon -q}"

# Arquivos que o agente nunca pode alterar no repositório de código
PROTEGIDOS=(CLAUDE.md AUTONOMO.md docs/ARCHITECTURE.md docs/DECISIONS.md .claude autonomo .github)

# Rótulos do GitHub usados no fluxo
ROTULO_REVISAO="aguardando-revisao"
ROTULO_MUDANCAS="pedir-mudancas"
ROTULO_FORTE="revisao-forte"
