## O que muda
Cria no `domain` o pacote `blob` com o value object `BlobRef(chave, sha256)`, o utilitário `Sha256`, a porta `BlobStore` (`gravar`/`ler`) e a `BlobConflitanteException`.
Adiciona o `BlobStoreEmMemoria` no `adapter-fake`, com estas regras: regravar o mesmo conteúdo é no-op; conteúdo diferente na mesma chave é rejeitado; sha que não bate com os bytes é rejeitado; os arrays são copiados na entrada e na saída.
É a base para a E2-S07 (o adapter grava o bruto) e para a E3-S05 (MinIO).

## Olhar com atenção
- `domain/src/main/java/br/com/doc/ingest/domain/blob/BlobStore.java:19` — define o contrato que o MinIO vai ter de cumprir (idempotência, conflito, sha conferido). Confira se essas regras são as que você quer congelar.
- `adapter-fake/src/main/java/br/com/doc/ingest/adapter/fake/BlobStoreEmMemoria.java:24` — a atomicidade vem do `compute` do `ConcurrentHashMap`, com a exceção lançada dentro da lambda (o mapeamento fica intacto). Está correto, mas é sutil.
- `domain/src/main/java/br/com/doc/ingest/domain/blob/BlobRef.java:16` — a validação da chave é simples: barra qualquer `..` (inclusive `a..b`) e não barra `\` nem `//`.
- `domain/src/main/java/br/com/doc/ingest/domain/blob/BlobConflitanteException.java:7` — é uma exceção no domain, apesar da convenção "falha por `Desfecho`". Aqui ela indica violação da invariante 7 (bug), não um desfecho de transporte. Confirme se concorda.
- `adapter-fake/src/test/java/br/com/doc/ingest/adapter/fake/BlobStoreEmMemoriaPropriedadesTest.java:66` — a concorrência só é testada com conteúdos iguais. A corrida entre conteúdos diferentes não está coberta.
- `adapter-fake/src/test/java/br/com/doc/ingest/adapter/fake/BlobStoreEmMemoriaTest.java:89` — este teste entrou depois da implementação (o `clone()` já existia no primeiro commit do fake), então nunca foi visto falhando.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 7 — blob imutável | `BlobStoreEmMemoriaTest.mesmaChaveConteudoDiferenteRejeitada`, `BlobStoreEmMemoriaPropriedadesTest.regravarNuncaAlteraConteudo` |
| 5 — grave primeiro (só preparação) | `BlobStoreEmMemoriaTest.gravarDepoisLerDevolveMesmosBytes` (a ordem real fica para a E2-S07) |
| 3 — XML fora do core | `ArquiteturaDominioTest` (verde com o pacote `blob`), `BlobRefTest` (o `BlobRef` só tem chave + sha) |
| 13 — sem segredo/conteúdo em exceção | `mesmaChaveConteudoDiferenteRejeitada` (`hasMessageNotContaining`) |

## Decisões do agente nesta subetapa
- DA-4: o `BlobStoreEmMemoria` fica no `adapter-fake` (main), e não em test-fixtures do domain.
- DA-5: o `Documento.blobRef` continua como `Optional<String>`. A troca para `BlobRef` fica para a E3 (auditoria A8).

## O que NÃO foi feito
- Trocar o `Documento.blobRef` para `Optional<BlobRef>` (A8 → E3).
- Montar as chaves do layout §8.1, gzip, cifra em repouso, MinIO e streaming de blobs grandes (E2-S07/E3).
- Usar o `BlobStore` no `FakeSourceAdapter` ou no `adapter-nfe-nacional`.
- Testar a concorrência com conteúdos diferentes na mesma chave (sugerido para a E3-S05).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
