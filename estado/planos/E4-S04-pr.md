## O que muda
- `CertificadoVigente.escolher` define o certificado vigente de cada titular: válido agora, com o maior `validoAte` (DA-53 e DA-55).
- `PoliticaAlertaVencimento` (marcos de 60/30/15/7/1 dias) e `VerificarVencimentosUseCase` emitem um alerta por marco, só para o vigente, e um `VENCIDO`. A ordem é notificar e depois registrar, então a entrega é "pelo menos uma vez".
- `RotacionarCertificadoUseCase` + `JdbiEstadoColeta.trocarCertificado` trocam o `certificado_id` por CAS (`is not distinct from`) **sem tocar** cursor, `ult_nsu`, `versao`, lease nem bloqueio.
- Tudo atrás de portas, com repositórios em memória. A tabela `certificado` e a dos alertas ficam para a E4-S02b.

## Olhar com atenção
- `infra-postgres/src/main/java/br/com/doc/ingest/infra/postgres/JdbiEstadoColeta.java:108` — é o único SQL novo, e o coração da inv. 1/2 nesta subetapa. Confira que o `set` não inclui `versao` e veja o `cast(:esperado as bigint)` para o caso nulo. A mutação `versao = versao + 1` foi testada e derruba a propriedade jqwik (PROGRESSO, 09:03).
- `domain/src/main/java/br/com/doc/ingest/domain/usecase/RotacionarCertificadoUseCase.java:44` e `:65` — a regra de tenant da DA-54: só troca se o atual for nulo ou do mesmo tenant. Uma chave nula de um CNPJ presente em dois tenants fica com quem rotacionar primeiro. Isso depende da D1 da `[DECISAO] E4-S03`.
- `domain/src/main/java/br/com/doc/ingest/domain/usecase/VerificarVencimentosUseCase.java:94` — a falha do notificador é engolida sem rastro. É o comportamento pedido (tenta de novo na próxima rodada), mas fica invisível até a E4-S05 expor isso.
- `domain/src/main/java/br/com/doc/ingest/domain/cofre/RepositorioCertificados.java:25` — `vencendoAte` traz todos os vencidos desde sempre. Com volume, a varredura cresce sem limite. A E4-S02b deve limitar isso no SQL.
- `domain/src/main/java/br/com/doc/ingest/domain/cofre/CertificadoVigente.java:35` — `Vencido` tem precedência sobre `AindaNaoValido` (DA-55). A E4-S03 vai mapear isso para `CERT_VENCIDO`.
- `cert-vault/src/testFixtures/java/br/com/doc/ingest/certvault/FakeEstadoColeta.java` — o fake de `estado_coleta` está nos fixtures do cert-vault, e os testes dos use cases de domínio rodam em `cert-vault/src/test`. Funciona, mas não é o lugar natural (o plano pedia `domain`).

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 Não-retrocesso de NSU | `JdbiEstadoColetaTrocaCertificadoTest.trocaNaoMexeEmCursorUltNsuVersaoLeaseNemBloqueio`, `CaosRotacaoTest.rotacaoEConfirmarLoteAoMesmoTempoNaoPerdemCursor`, `EstadoColetaTrocaPropriedadesTest.{trocasAleatoriasNuncaMudamCursorNemVersao, cursorNuncaRetrocede}`, `RotacionarCertificadoUseCaseTest.cursorEVersaoIntactosAposRotacao` |
| 2 Um CNPJ por vez | A troca não toca lease nem `versao` (mesmo teste acima, com lease ativo de outro worker); `CaosRotacaoTest.duasRotacoesConcorrentesTemUmVencedor`; `CaosDoisFetchersTest` continua verde |
| 6 Append-only | A porta `RegistroAlertasVencimento` só tem `registrarSeAusente`; `VerificarVencimentosUseCaseTest.alertaUmaVezPorMarco`; `RotacionarCertificadoUseCaseTest.rotacaoNaoApagaOAntigo` |
| 13 Nada de segredo em log | `VerificarVencimentosUseCaseTest.alertaNaoTemSegredo`; `RotacionarCertificadoUseCaseTest.rotacaoNaoChamaAbrir`; `ArquiteturaCofreTest` verde |
| 14 Nunca SEFAZ real | Só certificados sintéticos e Postgres em Testcontainers |

## Decisões do agente nesta subetapa
- DA-53: o vigente é o válido agora com o maior `validoAte`. O alerta sai uma vez por marco, só para o vigente; se vários marcos forem cruzados juntos, sai só o mais urgente; notifica e depois registra.
- DA-54: a rotação troca por CAS e só quando o certificado atual é nulo ou do mesmo tenant; caso contrário devolve `OutroTenant`. Não decide a D1.
- DA-55: com um certificado vencido e uma renovação futura, o resultado é `Vencido`, e não `AindaNaoValido`.

## O que NÃO foi feito
- Tabela `certificado`, tabela de alertas emitidos e os `Jdbi*` com RLS. Ficam para a E4-S02b (Q1 da `[DUVIDA] E4-S02`). O `RepositorioCertificadosContratoTest` já está pronto para rodar contra eles.
- Transição para `CERT_VENCIDO`/`AGUARDANDO_CERTIFICADO` e sua auditoria. Fica para a E4-S03.
- Auditoria da troca de certificado. Depende da D3 da `[DECISAO] E4-S03`.
- Escolha entre tenants diferentes para o mesmo CNPJ. Depende da D1.
- Canal real de notificação, agendamento dos dois use cases e chamada a `CacheSslContext.invalidar(fingerprintAntigo)`. Ficam para a composição no `app-worker` (E4-S05). Por ora há só o teste do cache e o Javadoc.
- Filial coberta pelo certificado da matriz; re-cifrar com KEK nova.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
