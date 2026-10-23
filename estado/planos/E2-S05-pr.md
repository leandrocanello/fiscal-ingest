## O que muda
Fecha a E2-S05 em `adapter-nfe-nacional/.../doczip`: `XmlSeguro` (StAX sem DTD/XXE), `ExtratorMetadados` + `MetadadosDfe`
(resNFe, procNFe, resEvento, procEventoNFe), conferência da raiz do XML contra o schema, `extrairSeConhecido` (schema
desconhecido não é descartado) e propriedades jqwik do decode. Os 4 primeiros passos (classificador, decoder, exceção,
teto de gzip) já entraram no PR #13. Nada muda no `domain`.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/XmlSeguro.java:45` — o bloqueio de DTD/entidade está só em `next()`; `nextTag()`/`getElementText()` não passam por ele. Ninguém os usa hoje, mas esta é a superfície de XXE do módulo.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/ExtratorMetadados.java:113` — os caminhos dentro de `infNFe` são casados com `indexOf("infNFe/")`. É o que separa `emit/CNPJ` de `dest/CNPJ`, base do roteamento (inv. 10).
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/ExtratorMetadados.java:120` (e 126, 141) — CPF é ignorado em emitente, `autXML` e autor do evento, como o plano pedia. Decidir antes da E3-S07 se produtor rural e `autXML` com CPF importam.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/doczip/ExtratorMetadadosTest.java:146` — o teste da inv. 5 é quase tautológico. Falta um docZip `DESCONHECIDO` válido passando pelo decoder com os bytes conferidos. Sugiro reforçar na E2-S07.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/doczip/MetadadosDfe.java:51` — o `toString` mascara o destinatário, mas o accessor devolve o CPF inteiro. Quem logar o campo direto vaza dado pessoal (inv. 13).

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 — XML fora do core | ArchUnit/`ArquiteturaDominioTest` existentes; diff vazio em `domain/` |
| 5 — nunca perder documento | `ExtratorMetadadosTest.schemaDesconhecidoEhClassificadoENaoDescartado` (fraco, ver acima) |
| 10 — atribuição pelo conteúdo (só preparo) | `ExtratorMetadadosTest.procNFeExtraiEmitenteDestinatarioAutXmlETransportador` |
| 13 — sem dado pessoal/conteúdo em log ou exceção | `ExtratorMetadadosTest.toStringMascaraCpf`, `chaveInvalida`; `XmlSeguroTest.xmlComDoctypeEhRejeitado` |
| §13 — parser sem XXE, zip bomb | `XmlSeguroTest.xmlComDoctypeEhRejeitado`; `DocZipDecoderTest.gzipAcimaDoLimiteEhRejeitado` (PR #13) |
| robustez do decode | `DocZipDecoderPropriedadesTest.idaEVoltaPreservaBytes`, `nuncaLancaOutraExcecao` |

## Decisões do agente nesta subetapa
- DA-6: teto de 10 MiB na descompactação; schema desconhecido vira `DESCONHECIDO` com os bytes mantidos; schema que diverge da raiz → `DocZipInvalidoException`.

## O que NÃO foi feito
- Gravar no `BlobStore`, sha256, montar `Documento`/`Desfecho` e decidir o status no ledger para `DESCONHECIDO` (fica para a E2-S07).
- Dígito verificador da chave, XSD oficial, XML-DSig (fora do escopo).
- Extração de CPF em emitente, `autXML` e autor do evento (ver acima).
- TDD parcial em 2 passos: no passo raiz×schema, a única falha vista foi o erro de compilação; as propriedades jqwik passaram de primeira, porque o código já existia.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
