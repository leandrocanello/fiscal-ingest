## O que muda
Processor da coleta (`app-worker`): lê o bruto do blob, faz o parse seguro (`ParserDfe` → `ParserDfeNfe`), roteia pelo conteúdo (`RoteadorConteudo`, inv. 10) e registra no catálogo de **cada tenant** que tem o CNPJ consultado. Documento sem vínculo fica em quarentena `SEM_EMPRESA`, que se fecha sozinha quando surge um vínculo.
Migrações novas: V10 (`quarentena`, com RLS e "só resolve") e V11 (função `tenants_do_cnpj_consultado`, `security definer`, com papel próprio `fiscal_roteador`). Reprocessar é sempre seguro.
Implementa as decisões D1 (a), D2 (a) e D3 (a) do PARA-VOCE de 2026-11-03.

## Olhar com atenção
- `domain/src/main/java/br/com/doc/ingest/domain/catalogo/RoteadorConteudo.java:54` — a **exceção à inv. 10** que você aprovou no D1 (a): no resumo, a empresa do CNPJ consultado recebe ENTRADA. Ela só vale para `EMPRESA`, então um `resNFe` de compra do **próprio escritório**, na consulta do escritório, cai em quarentena com aviso para cadastrar o fornecedor (`RoteadorConteudoTest.resumoNaoVinculaEscritorioConsultado`). A §7.3 diz "nota do escritório → empresa `ESCRITORIO`". Confirme se é isso mesmo ou se o escritório deve entrar aqui.
- `domain/src/main/java/br/com/doc/ingest/domain/catalogo/RoteadorConteudo.java:49` — filtro do D2 (a): o `autXML` só vincula empresa do tipo `EMPRESA`. As duas exceções (D1/D2) ainda não estão na §7.3/§2.2 do CLAUDE.md, e isso cabe a você.
- `infra-postgres/src/main/resources/db/migration/V11__tenants_do_cnpj_consultado.sql:34` — `alter function ... owner to fiscal_roteador` exige que o usuário do Flyway seja superuser ou membro do papel. Nos testes passa porque roda como superuser; com um usuário de migração restrito (E3-S03), pode falhar. A linha 19 tem a política `using (true)` só para esse papel, que só tem `select` em 4 colunas: vale conferir que ela não afrouxa a RLS dos outros papéis.
- `infra-postgres/src/main/resources/db/migration/V11__tenants_do_cnpj_consultado.sql:25` — a função olha só `empresa.ativo`, não `tenant.ativo`. Um tenant desativado continua recebendo notas.
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiQuarentena.java:34` — correção de corrida achada pelo caos (DA-62): `abrir` trava o documento (`for update`) e só insere se não houver vínculo. Depende de o `registrar` usar a mesma trava e de o `READ COMMITTED` dar um snapshot novo ao `insert`. A correção entrou no commit `test(app-worker): caos do processor`, não num `fix`.
- `app-worker/src/main/java/br/com/doc/ingest/worker/processador/ProcessadorColetado.java:83` — a decisão de abrir ou fechar a quarentena olha a **ficha** depois do registro, não o resultado. Por isso o reprocesso abre a quarentena que faltou.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/SituacaoNfe.java:18` — tabela de `cStat`/`cSitNFe`/`tpEvento` → situação, **[VALIDAR]**, ainda sem fonte em `docs/FONTES-OFICIAIS.md` (DA-33). É reconstruível do bruto.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/ParserDfeNfe.java:72` — no evento, o emitente sai da chave (posições 7–20), nunca do autor.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 · XML fora do core | `RegraSemBytesNoParserTest`, `ArquiteturaWorkerTest`, `ProcessadorColetadoTest.comandoEResultadoNaoCarregamBytes` |
| 4 · sem `cStat` no core | `ArquiteturaDominioTest.nenhumaClasseDoDominioFalaDeCStat`, `ArquiteturaWorkerTest.processadorNaoDependeDeAdapterNemDeInfra` |
| 5 · grave primeiro, nunca perder | `ProcessadorColetadoTest.{blobAusenteLancaENaoRegistra, schemaDesconhecidoNaoApagaNemRegistra, xmlInvalidoNaoRegistra}`, `CaosProcessadorTest.quedaEntreRegistrarEAbrirQuarentenaReprocessaSemPerda` |
| 6 · append-only | `CatalogoAppendOnlyTest` (intocado), `QuarentenaEsquemaTest.{papelDaAplicacaoSoAtualizaResolvidoEm, resolvidaNaoReabreNemEhApagadaNemMudaOutraColuna}` |
| 8 · um documento por (tenant, chave) | `ProcessadorColetadoIntegracaoTest.resumoECompletoViramUmDocumento`, `CaosProcessadorTest.processamentosConcorrentesDaMesmaNotaCriamUmDocumento`, `ProcessadorPropriedadesTest.{ordemDeChegadaNaoMudaAFicha, umDocumentoPorTenantEChave}` |
| 9 · parser seguro, confiança registrada | `ParserDfeNfeTest.{doctypeViraInvalido, entidadeExternaViraInvalido}`, `ProcessadorColetadoIntegracaoTest.provenienciaDaColetaEhOficialComLedger` |
| 10 · atribuição pelo conteúdo | `RoteadorConteudoTest`, `RoteadorConteudoPropriedadesTest`, `ProcessadorColetadoIntegracaoTest.{saidaPeloAutXmlDoEscritorioVaiParaAEmpresaEmitente, transferenciaEntreEmpresasTemDoisVinculos, notaNaoVazaParaTenantQueNaoConsultou}` |
| 13 · sem dado pessoal em log/exceção | `ParserDfeNfeTest.invalidoNaoEcoaConteudo`, `ProcessadorColetadoTest.falhaNaoExpoeCpfDoDestinatario`, `JdbiQuarentenaTest.falhaNaoExpoeParametros` |
| 14 · sem SEFAZ real | só fixtures sintéticas, Testcontainers e fakes |
| Isolamento de tenant (§13) | `QuarentenaRlsTest`, `TenantsDoCnpjConsultadoTest.{devolveSoIdsDeTenant, papelDaAplicacaoSemTenantNaoLeEmpresa}`, rodando como usuário sem superuser |

## Decisões do agente nesta subetapa
- DA-32: o processor fica no `app-worker`, sem Spring, só com portas; `ParserDfe` é porta do domínio, implementada no adapter.
- DA-33: tabela de situação da NF-e isolada em `SituacaoNfe`, marcada [VALIDAR]; código desconhecido vira `SO_RESUMO`.
- DA-61: trigger `quarentena_so_resolve`, além do grant por coluna; `abrir` devolve `boolean` e `resolverDoDocumento` devolve `int`.
- DA-62: `JdbiQuarentena.abrir` trava o documento e não abre se já houver vínculo (corrida achada pelo caos).

## O que NÃO foi feito
- Consumir `docs.coletados` e publicar `catalogo.mudancas` no outbox → E3-S08 (a E3-S04 não está nesta base).
- Aviso ao tenant ("cadastre o CNPJ X"), gatilho de reprocesso ao cadastrar empresa e DLQ → E3-S08/E4. Hoje o reprocesso é manual, e `SemTenant`/`Invalido` só voltam para quem chamou.
- Verificação de consentimento `COLETA_DFE` antes de registrar → E4-S03.
- `set_config` privado em vez de `TransacaoPorTenant` → depois do merge da E3-S03. A V10/V11 têm grants condicionais a `fiscal_app` (V4 da E3-S03). **Mescle E3-S03/E3-S04 antes**, senão um banco já migrado quebra (achado 2 da E3-S06).
- Vínculo por raiz de CNPJ (filial sem cadastro fica em quarentena) e `tpNF = 0` como exceção à direção.
- Conferência dos códigos no MOC e registro em `docs/FONTES-OFICIAIS.md` → smoke E2-S12.
- CLAUDE.md §7.3/§2.2 sem as exceções D1/D2 (cabe ao autor).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
