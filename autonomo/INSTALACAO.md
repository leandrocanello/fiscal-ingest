# Instalação do modo autônomo (noites 3 e 4 do AUTONOMO.md §13)

> Pré-requisitos: PRs `fase-1.5/build`, `fase-1.5/docs` e `automacao/estrutura` mergeados no `main`,
> e `./gradlew test` verde no seu PC.
> Tempo estimado: 60–80 min. Itens **[VERIFICAR]** ainda não foram testados no Windows.

## 1. Distro WSL dedicada (PowerShell do Windows, como administrador)
```powershell
wsl --install Ubuntu-24.04 --name fiscal-dev     # [VERIFICAR] --name exige WSL recente; senão: wsl --install -d Ubuntu-24.04
# Crie o usuário "dev" quando pedir.
```

## 2. Isolamento (dentro da distro: `wsl -d fiscal-dev`)
```bash
sudo tee /etc/wsl.conf >/dev/null <<'EOF'
[automount]
enabled = false
[interop]
enabled = false
appendWindowsPath = false
[user]
default = dev
EOF
exit
```
No PowerShell: `wsl --terminate fiscal-dev` e entre de novo. Confirme: `ls /mnt/c` deve estar vazio ou não existir.

> Com `interop` desligado, `wsl.exe -d fiscal-dev -- comando` (usado pelo Agendador) **continua funcionando**:
> o que é bloqueado é o caminho inverso (Linux chamando .exe do Windows).

## 3. Ferramentas
```bash
sudo apt update && sudo apt install -y openjdk-21-jdk git jq gh curl util-linux coreutils
curl -fsSL https://claude.ai/install.sh | bash        # instalador do Claude Code [VERIFICAR o comando atual na doc]
echo 'export CLAUDE_CONFIG_DIR=$HOME/.claude-pessoal' >> ~/.bashrc && source ~/.bashrc
claude                                               # faça login com a conta PESSOAL (Pro/Max) e saia com /exit
```
Docker Desktop (para Testcontainers, a partir da Etapa 3): Settings → Resources → WSL integration → ligar **só** `fiscal-dev`.

## 4. GitHub com token restrito
1. github.com → Settings → Developer settings → **Fine-grained tokens** → Generate:
   - Repository access: **Only select repositories** → `fiscal-ingest`
   - Permissions: **Contents: Read and write**, **Pull requests: Read and write**, **Issues: Read and write** (rótulos), Metadata: Read
   - Validade: 90 dias (anote para renovar)
2. No WSL: `gh auth login --with-token` (cole o token) e `gh auth setup-git`.
3. No GitHub, proteção do `main` (Settings → Rules): exigir PR, **0 aprovações** (você não pode aprovar PR do próprio token),
   bloquear force push e exclusão.

## 5. Identidade dos commits
```bash
git config --global user.name "fiscal-autonomo (Claude)"
git config --global user.email "seu-email+autonomo@..."   # algo que identifique os commits do agente
```

## 6. Instalar
```bash
git clone https://github.com/leandrocanello/fiscal-ingest.git ~/fiscal-motor
bash ~/fiscal-motor/autonomo/bin/instalar.sh
```
Cria `~/fiscal-motor` (scripts, sempre no main), `~/fiscal-ingest` (onde o agente trabalha) e `~/fiscal-estado`
(branch `auto/estado`: ROADMAP, PARA-VOCE, planos, relatórios), além dos rótulos do GitHub.

## 7. Primeiro teste manual (com você olhando)
```bash
MAX_CHAMADAS_JANELA=1 ~/fiscal-motor/autonomo/bin/janela.sh
tail -30 ~/fiscal-estado/estado/logs/*.log
cat ~/fiscal-estado/estado/PROGRESSO.md
```
Repita algumas vezes, olhando os commits em `~/fiscal-ingest` e o PR que aparecer no GitHub.
**[VERIFICAR]** quando bater o limite de uso, olhe o JSON em `estado/logs/*-*.json` e ajuste a regex de `bin/resultado.sh`.

## 8. Agendar (PowerShell do Windows, usuário normal)
```powershell
cd <onde estiver o repositório no Windows>\autonomo\windows
.\agendar.ps1
```
Registra as janelas das 08:00 e 13:00 + fechamento às 17:50 (ADR-029). Se o limite semanal acabar antes de sexta, reduza `MAX_CHAMADAS_JANELA` em `autonomo/config.sh`.
Energia: desative a suspensão em horário comercial ou ative "temporizadores de ativação". Deixe o usuário logado (tela bloqueada serve).

## Operação do dia a dia
- **Parar tudo:** crie o arquivo `estado/PARAR` na branch `auto/estado` (pelo GitHub mesmo). Apague para voltar.
- **Pedir mudanças num PR:** comente e adicione o rótulo `pedir-mudancas`. **Aprovar:** merge.
- **Responder o agente:** edite `estado/PARA-VOCE.md` na branch `auto/estado` e ajuste o status no `ROADMAP.md`.
- **Relatório do dia:** `estado/revisao/AAAA-MM-DD.md`.
