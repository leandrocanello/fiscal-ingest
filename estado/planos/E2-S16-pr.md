## O que muda
Rejeições do `consChNFe` (MOC 7.0 Tab. 5-29; sua decisão de 2026-10-29, opção a) deixam de cair no fallback `IndisponivelTemporario(5 min)`:
`217`, `236`, `614`–`619`, `640`, `641`, `653`, `654` e `252` → `IndisponivelDefinitivo` (motivo legível com o código); `593` → `ErroAutenticacao`.
Só o `CStatMapper` e os testes mudam (tabela parametrizada, propriedades, ausência de WARN e um teste ponta a ponta com WireMock).

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:64` — o mesmo `CStatMapper` atende `distNSU` e `consChNFe`. Se um `252` ou `593` vier no lote, o CNPJ vai para `ERRO_DEFINITIVO`/`CERT_*` (§6.7) em vez de retentar a cada 5 min. Parece correto (são erros de configuração ou de credencial), mas muda o comportamento do lote.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapper.java:82-96` — os textos dos motivos e o agrupamento. `236` tem um `case` próprio com o mesmo texto de 614–619 (cosmético). Confira se "NF-e cancelada/denegada" serve para 653 e 654.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapper.java:95` — `593` como `ErroAutenticacao` leva ao estado `CERT_*` e para a coleta até o tenant agir. É o que se quer para "raiz do CNPJ ≠ certificado"?
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapperTest.java:38` — o javadoc não cita 252/593 (falta de documentação menor).

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 · Não-retrocesso de NSU | `CStatMapperPropriedadesTest.soCodigosDeSucessoViramOkOuVazio` (os 14 códigos em `MAPEADOS`) |
| 4 · Core desacoplado da origem | tradução só no adapter; `CStatMapperTest.tabelaDeCodigosMapeados`, `CasosConfiguracao`; ArchUnit existente |
| 13 · Sem segredo/XML em log | `CStatMapperTest...LogDoFallback.codigosDoConsChNFeNaoLogam`; motivos sem CNPJ |
| 14 · Sem SEFAZ real | `NfeNacionalAdapterTest.chaveRejeicoesDaTab529` (WireMock) |

## Decisões do agente nesta subetapa
- nenhuma

## O que NÃO foi feito
- `472`, `489`, `490` e outros códigos da Tab. 5-29 fora da decisão: seguem no fallback (fora do escopo).
- O comportamento "não bloquear o CNPJ" para o `IndisponivelDefinitivo` de consulta pontual fica para o scheduler (Fase 4).
- `docs/FONTES-OFICIAIS.md` não foi atualizado (era opcional), e a lista do CLAUDE.md §3.2 não cita os novos códigos: precisa ser editada pelo autor.
- Os textos não foram conferidos contra uma NT mais nova que o MOC 7.0 (fica para o smoke E2-S12).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
