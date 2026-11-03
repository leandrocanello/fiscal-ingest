## O que muda
Cria o catálogo: tabelas `documento`, `arquivo`, `documento_empresa` e `proveniencia` (V8), com RLS, append-only e grants (V9).
Adiciona a porta `Catalogo` e os tipos em `domain.catalogo`, a função pura `FusaoDocumento` e a implementação `JdbiCatalogo`.
`registrar` é atômico e idempotente: a mesma nota por vários canais vira um documento com várias proveniências.
Ainda não há chamador: parser, roteamento e outbox ficam para a E3-S07.

## Olhar com atenção
- `domain/src/main/java/br/com/doc/ingest/domain/catalogo/FusaoDocumento.java:49` — **principal ponto.** Dentro da mesma classe de confiança, fica o primeiro valor presente. A ordem só não importa se os registros forem coerentes, e é só isso que as propriedades testam. Se a E3-S07 passar em registro de evento a versão do evento como `versaoLayout`, ou `dhEvento` como `emissao`, o documento passa a depender da ordem de chegada. Avalie se basta o contrato com a E3-S07 ou se a fusão deve se proteger.
- `domain/src/main/java/br/com/doc/ingest/domain/catalogo/FusaoDocumento.java:25` — regra da DA-27: `DECLARADO` não altera documento confiável, e confiável substitui tudo o que era declarado. Decisão do agente que define a semântica do catálogo; vale sua leitura.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiCatalogo.java:70` — um registro `DECLARADO` também grava vínculo e arquivo (DA-27). O documento fica sinalizado por `so_declarado`, mas nada aqui impede que chegue ao integrador: a E3-S07/E5 precisa tratar isso (inv. 9).
- `infra-postgres/src/main/resources/db/migration/V9__catalogo_rls.sql:37` — grants condicionais à existência de `fiscal_app`. Migrations fora de ordem: se V8/V9 entrarem antes de V4–V7, um banco já migrado falha na validação do Flyway (`Migracoes` não usa `outOfOrder`) e os grants da V9 não são reaplicados. Em banco novo não há problema. Recomendo mesclar E3-S03 e E3-S04 antes, ou recriar os bancos de dev.
- `infra-postgres/src/main/resources/db/migration/V8__catalogo.sql:62` — FK para `ledger_nsu (id)` (DA-29). Limita um particionamento futuro do ledger, porque a chave teria de incluir a coluna de partição.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiCatalogo.java:106` — traduz só `JdbiException`. `IllegalStateException` de `.one()` e `IllegalArgumentException` de mapeamento passam direto, contra o `@throws` da porta. Além disso, a javadoc de `FalhaCatalogoException` diz "pode ser reprocessada", mas uma FK violada falha sempre, e sem a causa o diagnóstico fica só no SQLState (escolha pela inv. 13).
- `domain/src/main/java/br/com/doc/ingest/domain/catalogo/MetadadosDocumento.java:58` — `toString` mostra o CNPJ emitente em claro, contra a §20 (CNPJ mascarado em log). Segue o `Cnpj.toString` existente, por isso não bloqueei; corrigir no `Cnpj` resolve os dois.
- `infra-postgres/src/test/java/br/com/doc/ingest/infra/postgres/UsuarioAppCatalogo.java:24` — helper de login duplicado com `CatalogoRlsTest.java:55`; o plano pedia privado. Consolidar depois do merge da E3-S03. Limpeza simples, sem risco.
- `CLAUDE.md` §8 — o rascunho não traz `documento.so_declarado` nem `tenant_id` em `arquivo`, `documento_empresa` e `proveniencia` (DA-27). Só você pode atualizar esse arquivo.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 — sem XML no domínio | `RegraSemBytesNoCatalogoTest` |
| 6 — proveniência append-only | `CatalogoAppendOnlyTest.updateEDeleteEmProvenienciaSaoRecusados`, `CatalogoRlsTest.papelDaAplicacaoNaoTemUpdateDeleteNemTruncateEmProveniencia` |
| 7 — blob imutável (a referência não muda) | `CatalogoAppendOnlyTest.updateEmArquivoEhRecusado`, `JdbiCatalogoTest.mesmoShaMantemPrimeiroBlobRef` |
| 8 — um documento por `(tenant, chave)` | `CatalogoEsquemaTest.segundoDocumentoMesmoTenantEChaveEhRecusado`, `JdbiCatalogoTest.mesmaNotaPorDoisCanaisViraUmDocumentoComDuasProveniencias`, `CaosCatalogoTest.registrosConcorrentesDaMesmaChaveCriamUmDocumento`, `CatalogoPropriedadesTest.umDocumentoPorChave` |
| 9 — push não confiável; confiança registrada | `RegistroArquivoTest.pushNaoPodeSeDeclararOficial`, `CatalogoEsquemaTest.proveniencia*`, `FusaoDocumentoTest.declaradoNaoAlteraDocumentoConfiavel`, `FusaoDocumentoPropriedadesTest.ordemDeChegadaNaoMudaOResultado`. Parser e assinatura ficam para as próximas subetapas |
| 10 — atribuição pelo conteúdo (só a estrutura) | `JdbiCatalogoTest.transferenciaEntreEmpresasDoTenantTemDoisVinculos`, `JdbiCatalogoTest.documentoSemEmpresaFicaGuardadoSemVinculo`. O roteamento é da E3-S07 |
| 13 — sem dado pessoal em log/exceção | `MetadadosDocumentoTest.toStringMascaraDestinatario`, `JdbiCatalogoTest.falhaDeSqlNaoExpoeDestinatario` |
| Isolamento de tenant (ADR-013, §13) | `CatalogoRlsTest`, como usuário sem superuser |

**Verificação do revisor:** `:domain:test` e `:infra-postgres:test` reexecutados com `--rerun`, verdes; total de 667 testes, 0 falhas (os demais módulos vieram do cache do Gradle).
**Não verificado:** merge com E3-S03/E3-S04 e V1–V9 aplicadas juntas. A simulação foi barrada pelo hook; só li as migrations daquelas branches. A compatibilidade com o `TodaTabelaComTenantIdTemRlsTest` foi conferida por leitura.

## Decisões do agente nesta subetapa
- DA-27: `tenant_id` com FK composta nas quatro tabelas; regras de proveniência; fusão "confiáveis ou, se não houver, declarados"; numeração V8/V9; `set_config` privado no `JdbiCatalogo`.
- DA-28: `fundir(atual, metadados, situacao, confianca)` + `inicial(...)`.
- DA-29: FK de `proveniencia.ledger_nsu_id` para o ledger; `valor_total` como `numeric` sem escala.
- DA-30: `FalhaCatalogoException` em `domain.catalogo`, com `codigo()` genérico.
- DA-31: `ShrinkingMode.FULL` em `CatalogoPropriedadesTest`, para evitar deadlock no `truncate` depois de uma falha. `EstadoColetaCasPropriedadesTest` e `JdbiNsuLedgerPropriedadesTest` têm o mesmo desenho e não foram alterados. A causa foi inferida, não lida no código do jqwik.

## O que NÃO foi feito
- Parser, roteamento pelo conteúdo, tabela `quarentena` e mapeamento `cSitNFe`/evento → `SituacaoDocumento`: E3-S07.
- Outbox/`catalogo.mudancas` na transação do registro: E3-S07, depois do merge da E3-S04 (o `ResultadoRegistro` já traz as flags).
- Troca do `set_config` privado por `TransacaoPorTenant`: depois do merge da E3-S03.
- FK de `proveniencia.recebimento_id` e tabela `recebimento`: E5-S02. `inutilizacao`, `feed` e `consentimento`: suas subetapas.
- Preencher `ledger_nsu_id` a partir do fetcher real: E3-S08. Tipos fortes em `domain.model.Documento` e troca de `ChaveAcessoNfe`: E3-S07/E3-S08.
- Expurgo por retenção (§14): fase própria; exigirá migration e auditoria por causa dos triggers.
- Particionamento e índices de consulta da API: com medição, ou E5-S05.
- Vínculos em ordem fixa, previstos no passo 9: não foram necessários, porque a trava do documento já serializa os escritores (PROGRESSO, passo 9).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
