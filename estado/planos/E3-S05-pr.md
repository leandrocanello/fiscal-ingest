## O que muda
Novo módulo `infra-blob-s3`: implementação real da porta `BlobStore` sobre API S3 compatível (AWS SDK v2 + `url-connection-client`),
testada contra SeaweedFS `4.53` (Testcontainers 2.0.2, autenticação SigV4 ligada com identidade sintética).
Imutabilidade por `PUT` com `If-None-Match: *` (412 → compara sha: igual = no-op, diferente = `BlobConflitanteException`); sha256
conferido antes de qualquer I/O e de novo na leitura; falhas de infraestrutura viram `BlobStoreIndisponivelException`.
Suíte abstrata `BlobStoreContrato` (testFixtures do `adapter-fake`) roda contra a memória e o S3, mais uma propriedade jqwik de equivalência.

## Olhar com atenção
- `infra-blob-s3/src/main/java/br/com/doc/ingest/infra/blob/S3BlobStore.java:51-67` — o coração da inv. 7: escrita condicional e
  tratamento do 412. Qualquer outro status é relançado e vira `BlobStoreIndisponivelException` (inclui um eventual 409 de escrita
  concorrente: retentável, não conflito).
- `infra-blob-s3/src/main/java/br/com/doc/ingest/infra/blob/S3BlobStore.java:72-79` — no 412 o sha existente vem do **metadado**,
  não dos bytes. Objeto adulterado mantendo o metadado: `gravar` diz "idempotente" e `ler` devolve vazio, sem distinguir corrupção
  de ausência. Mesmo contrato da memória; vale um alerta em subetapa futura.
- `infra-blob-s3/src/test/java/br/com/doc/ingest/infra/blob/S3BlobStoreTest.java:146-152` (DA-60) — o SeaweedFS **cria o bucket
  implicitamente** no `PUT`; por isso o teste de bucket inexistente só prova a falha na leitura. Em produção, bucket com nome errado
  grava no lugar errado sem erro → sugiro `HeadBucket` na inicialização (E3-S08/`app-*`).
- `adapter-fake/src/testFixtures/java/br/com/doc/ingest/adapter/contrato/BlobStoreContrato.java:160` — caso concorrente (8 threads,
  mesma chave). Verde contra o SeaweedFS em 6 execuções (suíte + 5 reexecuções do revisor), mas a atomicidade do `If-None-Match` no
  SeaweedFS só está provada empiricamente, não pela documentação dele.
- `infra-blob-s3/src/main/java/br/com/doc/ingest/infra/blob/BlobStoreIndisponivelException.java:7` — a mensagem traz a chave, e a
  chave do pull tem o CNPJ sem máscara (§20). Não é segredo (inv. 13), mas mascare no ponto de log.
- `infra-blob-s3/src/test/java/br/com/doc/ingest/infra/blob/SeaweedFsTestBase.java:21` — tag fixa num só lugar (DA-11/DA-57).
  `novoCliente` não usa `FabricaClienteS3` (duplicação pequena; os timeouts de produção só aparecem em `FabricaClienteS3Test`).

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 5 — grave primeiro / sem perda | `S3BlobStoreTest.falhaDoServidorLancaENaoDevolveRef`, `bucketInexistenteLanca`; `BlobStoreContrato.gravarDepoisLerDevolveMesmosBytes` |
| 7 — blob imutável | `SeaweedFsCompatibilidadeTest.putComIfNoneMatchEmChaveExistenteDevolve412`; `BlobStoreContrato.{mesmaChaveConteudoDiferenteRejeitada, gravacoesConcorrentesNaMesmaChaveSoUmaVence}` (memória e S3); `S3BlobStoreTest.regravar*`; `S3BlobStorePropriedadesTest` |
| 13 — sem segredo em log/exceção | `S3ConfigTest.{toStringNaoExpoeSecretKey, endpointComCredencialNaUrlEhRecusado}`; `S3BlobStoreTest.excecaoNaoTrazSecretNemBytes` |
| 14 — sem serviço real em teste | Só Testcontainers; `S3ConfigTest.exigeEndpointBucketECredenciais` (`endpointOverride` obrigatório) |
| Camadas (§19, ADR-027) | `ArquiteturaBlobTest.{infraBlobNaoUsaSpring, dominioNaoDependeDoSdkS3}` |

## Decisões do agente nesta subetapa
- DA-57: SeaweedFS `chrislusf/seaweedfs:4.53` (tag fixa, só em `SeaweedFsTestBase`), autenticação ligada; módulo `infra-blob-s3`. O checksum CRC32 padrão do SDK funcionou sem `WHEN_REQUIRED`.
- DA-58: timeouts padrão de 5 s (conexão) e 30 s (chamada); `secretKey` clonada e mascarada no `toString`.
- DA-59: `checksumSHA256` enviado no `PUT` (o SeaweedFS aceitou), além do metadado `sha256`.
- DA-60: o SeaweedFS cria bucket implicitamente no `PUT`; o teste de bucket inexistente só prova a falha em `ler`.

## O que NÃO foi feito
- Ligar o `S3BlobStore` ao `NfeNacionalAdapter`/fetcher e ao `app-worker` → E3-S08 / `app-*`.
- Layout `raw/push/` e `doc/{tenant}/...` e gzip dos normalizados → E3-S07 / Fase 5.
- Política de bucket (sem criação implícita), versionamento/WORM, ciclo de vida, criptografia em repouso, docker-compose com SeaweedFS → Fase 10.
- Streaming de objetos grandes (a porta usa `byte[]`), expurgo de blob → só quando necessário / subetapa própria.
- Não existe distinção "corrompido × inexistente" na leitura, nem alerta de corrupção (ver "Olhar com atenção").
- "Ver o teste falhar antes" não é verificável pelo histórico; a revisão conferiu teste + produção no mesmo commit.

Verificação do revisor: `./gradlew test` verde (1115 testes, 0 falhas); caso concorrente reexecutado 5× contra o SeaweedFS, sempre verde.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
