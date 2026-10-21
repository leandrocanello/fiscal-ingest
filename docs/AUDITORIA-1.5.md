# Auditoria da Fase 1.5 — estado do código recuperado (2026-10-21)

> Feita numa sessão em nuvem sem acesso ao Maven Central/Gradle Portal. **Compilação verificada**
> para `domain` e `adapter-fake`; **testes não rodaram aqui**. Primeiro passo local: `./gradlew test`.

## 1. O que foi corrigido nesta branch (`fase-1.5/build`)

| Commit | Problema | Correção | Verificado? |
|---|---|---|---|
| build: remove Lombok | Plugin aplicado a todos os módulos (inclusive o `domain`), sem nenhum uso | Removido | Sim (build configura) |
| build: wrapper | Faltavam `gradlew`, `gradlew.bat` e `gradle-wrapper.jar`; `settings` com 6 módulos inexistentes | Wrapper 8.10.2; módulos previstos comentados; `.gitattributes` (LF) | Sim |
| fix: BuscarUmResultado | Tipo usado por 5 arquivos, **não recuperado** → `domain` não compilava | Recriado como `record (Optional<Documento>, Desfecho)` | Compila |
| fix: build do adapter-fake | Sem `build.gradle.kts` → não enxergava o `domain` | Criado | Compila |
| fix(nfe): package-info | Faltava `@XmlSchema(elementFormDefault = QUALIFIED)` → filhos do XML não seriam lidos | Recriado | **Não** (depende do Maven) |

Contagem de testes (por anotação) bate com os docs: `domain` 35 `@Test` + 1 parametrizado (≈38 casos),
`adapter-fake` 5, `adapter-nfe-nacional` 10 + 5.

## 2. Achados que NÃO foram corrigidos (precisam de decisão/teste primeiro)

| # | Onde | Achado | Risco | Proposta |
|---|---|---|---|---|
| A1 | `CStatMapper` | **`108` mapeado para `Ok` ("serviço em operação")**. Na NF-e, `107` = serviço em operação; `108` = serviço **paralisado momentaneamente**; `109` = paralisado sem previsão **[VALIDAR no MOC]** | Alto: tratar indisponibilidade como sucesso pode avançar cursor com resposta sem documentos | Mudar o teste primeiro: `108` → `IndisponivelTemporario`, `109` → `ServicoFora`; incluir em E2 |
| A2 | `CStatMapper` | `632` → `LimiteAtingido` (conflito já registrado no CLAUDE.md §3.2) | Médio | Decidir após `FONTES-OFICIAIS.md` |
| A3 | `CStatMapper` | Sem `589` (NSU maior que o máximo) → cai em `IndisponivelTemporario` e seria retentado para sempre | Médio | Novo desfecho/erro de consistência (CLAUDE.md §3.2) |
| A4 | `AvancarPonteiroUseCase` | Aceita qualquer `proximoCursor` em `Ok`/`Vazio`; **não há guarda nem teste de não-retrocesso** (invariante 1) | Alto | Teste de propriedade "cursor nunca decresce" + guarda (Cursor é opaco: precisa de um comparador fornecido pelo adapter ou NSU explícito no resultado) — design a propor |
| A5 | `RecuperarGapUseCase` | `Vazio` no consNSU vira `DESCARTADO` no ledger | Médio | Validar o que o AN responde para NSU inexistente; talvez `FORA_PRAZO`/`GAP` |
| A6 | `Credencial` | Ainda `caminho` + `senha` (v1). CLAUDE.md §9 define bytes do `.pfx` + senha em memória | Médio | Ajustar em E2-S08 |
| A7 | `ContextoConsulta` | Só `fonte` + `cnpj`; v2 pede `cnpj_consultado` + `certificado_id` + ambiente | Baixo agora | Ajustar em E2 |
| A8 | `Documento` | `modelo`/`tipoConteudo` como `String`, `blobRef` como `Optional<String>`, campo `cnpjDestinatario` (v2: atribuição pelo conteúdo) | Baixo agora | Tipos fortes quando o catálogo entrar (E3) |
| A9 | `adapter-nfe-nacional/build.gradle.kts` | **CXF 3.5.5 é baseado em `javax.*`**, enquanto o JAXB do projeto é `jakarta.*` 4.x; CXF não é usado em nenhum arquivo | Médio (conflito de classpath futuro) | Remover CXF (decisão já recomendada: HTTP + JAXB + Santuario) |
| A10 | `TokenBucket` | Recarga calculada a partir do último consumo: progresso parcial se perde a cada consumo; além disso o modelo da SEFAZ é "bloqueio por CNPJ", não balde | Baixo | Rever quando o scheduler entrar (E4) |
| A11 | `SourceAdapter` | Não tem `manifestar` (previsto nos docs) | — | E2-S09 |
