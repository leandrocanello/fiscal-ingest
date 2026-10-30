## O que muda
`Documento.blobRef` passa de `Optional<String>` a `Optional<BlobRef>` (chave + sha256), conforme a decisão do autor no PARA-VOCE E2-S08, opção (a).
O NF-e e o fake devolvem o `BlobRef` exatamente como o `BlobStore.gravar` o devolveu, e `buscarUm` e `buscarPorChave` repassam o mesmo ref do lote.
A suíte de contrato perde o gancho `lerBlob`: lê via `blobs().ler(ref)` e ganha o caso `blobRefConfereComOHashDosBytes` (inv. 7).
Fecha a DA-5 e a parte `blobRef` do achado A8.

## Olhar com atenção
- `domain/src/main/java/br/com/doc/ingest/domain/model/Documento.java:24` — mudança de tipo no record público do domínio. Todo consumidor futuro (processor/E3) lê com `blobStore.ler(doc.blobRef().get())`. Confirme que é esse o contrato que você quer congelar.
- `adapter-fake/src/testFixtures/java/br/com/doc/ingest/adapter/contrato/SourceAdapterContrato.java:86` — o novo caso da inv. 7 roda nos dois adapters. Ele se sobrepõe em parte a `todoDocumentoTemBlobLegivel` (l. 72); dá para fundir os dois mais tarde.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapter.java:191` — a única mudança de produção no adapter: `Optional.of(ref)` em vez de `ref.chave()`. A l. 217 (`buscarPorChave`) já repassava `d.blobRef()`.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/NfeNacionalAdapterTest.java:217` — `refsDevolvidos.get(i + 1)` pressupõe que o envelope é gravado primeiro. Isso é coerente com a inv. 5, mas acopla este teste à ordem de gravação.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3 — XML fora do core | `DocumentoTest.documentoLevaBlobRefComChaveESha` + ArchUnit `ArquiteturaDominioTest` |
| 5 — grave primeiro | contrato `todoDocumentoTemBlobLegivel` + `NfeNacionalAdapterTest` envelope primeiro (mantidos) |
| 7 — blob imutável / sha na referência | contrato `blobRefConfereComOHashDosBytes` (fake e NF-e) + `NfeNacionalAdapterTest.blobRefDoDocumentoEhOMesmoDevolvidoPeloBlobStore` + `buscarUmEBuscarPorChaveDevolvemOMesmoBlobRefDoLote` |

## Decisões do agente nesta subetapa
- DA-22: registra que a E2-S15 fecha a DA-5, aplicando a opção (a) escolhida pelo autor. Não é decisão nova do agente.

## O que NÃO foi feito
- Tipos fortes para `modelo`, `tipoConteudo` e `cnpjDestinatario` (resto do A8): ficam para a E3-S07.
- Validação de nulos no construtor do `Documento`: fora do escopo, porque os testes do domain passam `null`.
- Layout de chaves (`ChavesBlobPull`) e `BlobStore` sem mudança.
- Passos 2, 3 e 6 ficaram verdes de primeira: são testes de regressão do formato, cobertos pela troca de tipo do passo 1 (registrado no PROGRESSO).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
