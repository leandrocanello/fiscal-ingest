## O que muda
Este PR passa a aceitar o CNPJ alfanumérico da NT Conjunta 2025.001, em produção desde 06/07/2026. Hoje uma nota com esse CNPJ é recusada na extração, e um e-CNPJ alfanumérico vira `SEM_CNPJ` no cofre.
- `Cnpj` aceita `[A-Z0-9]{12}[0-9]{2}` com DV de módulo 11 (valor = ASCII − 48). O `toString` passa a mascarar.
- `ChaveAcesso` aceita `[0-9]{6}[A-Z0-9]{12}[0-9]{26}`. O DV é obrigatório só no que sai para a SEFAZ.
- O extrator, o evento, as chaves de blob, o cofre (`ExtratorCnpjCertificado`) e o banco (V13) passam a aceitar o novo formato.

Suíte inteira verde: 1173 testes, incluindo Testcontainers.

## Olhar com atenção
- `infra-postgres/src/main/resources/db/migration/V13__cnpj_chave_alfanumerica.sql:17`: o CHECK de `ledger_nsu.chave_acesso` repete a regex do domínio (`ChaveAcesso.java:15`).
  - Se as duas divergirem, o INSERT do ledger falha, o lote é relido e o CNPJ trava. Não há perda, mas também não há avanço.
  - A V13 também cria CHECKs em tabelas que já existem. Num banco com dado fora do formato, a migração falha. Hoje não há produção.
- `domain/src/main/java/br/com/doc/ingest/domain/model/ChaveAcesso.java:17`: o construtor confere só o formato, sem o DV (DA-38). Uma chave com DV errado vinda do AN entra no catálogo.
  - Isso foi escolhido para nunca perder documento.
  - Confira se concorda. A porta push (Fase 5) vai usar `temDvValido()`.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/evento/IdEvento.java:20`: o comportamento ficou mais estrito. Antes só conferia o formato; agora exige DV certo, e o DV duplicado de `EnvioManifestacao:104` saiu.
  - Os testes de assinatura trocaram as chaves para o DV certo, mudando só o último dígito.
- `domain/src/main/java/br/com/doc/ingest/domain/model/Cnpj.java:25`: `LETRAS_VEDADAS` está vazia de propósito (DA-37). A exclusão de I, O, U, Q e F não foi confirmada pela Receita.
  - Além disso, `A` e `6` colidem no DV, um limite do algoritmo documentado em `CnpjTest.aColisaoAe6NaoEhDetectavelPeloDv`.
- `domain/src/main/java/br/com/doc/ingest/domain/catalogo/MetadadosDocumento.java:32`: há uma assimetria entre os dois CNPJs.
  - O destinatário é conferido só pelo formato.
  - O emitente é um `Cnpj` e exige DV.
  - Por isso o processor da E3-S07 precisa filtrar com `Cnpj.valido`, ou um emitente com DV errado derruba o lote.
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/ExtratorCnpjCertificado.java:83-88`: este método está fora do escopo original; entrou pela devolução da revisão.
  - O laço `0`–`9` saiu e a validação ficou só em `Cnpj.valido`.
  - Com DV errado, o método agora devolve vazio em vez de lançar exceção. Isso é coberto por `dvErradoDevolveVazio`.
  - A PARA-VOCE "E1-S07 → E4-S02" (opção (a)) previa essa correção na E4-S02. Ela foi antecipada porque o cert-vault já está na `main`.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 e 2. Não-retrocesso, um CNPJ por vez | `NaoRetrocessoAlfanumericoTest` (CAS do cursor e ledger com CNPJ alfanumérico) |
| 5. Grave primeiro, sem perda | `ExtratorMetadadosTest.chaveComDvErradoAindaEhExtraida` (DV errado não descarta o documento) |
| 7. Blob imutável, com chave determinística | `ChavesBlobPullTest.cnpjAlfanumericoEntraNaChave` |
| 8. Um documento por (tenant, chave) | `JdbiCatalogoTest.mesmaChaveAlfanumericaViraUmDocumento` |
| 10. Atribuição pelo conteúdo | `ExtratorMetadadosTest.leCnpjsAlfanumericos`, `MetadadosDocumentoTest.emitenteEDestinatarioAlfanumericos` |
| 13. Sem segredo ou dado pessoal em log | `CnpjTest.invalidoEhRecusadoSemEcoarOValor`, `CnpjTest.toStringMascara`, `MetadadosDocumentoTest.toStringMascaraOEmitente`, `MetadadosDfeTest.toStringMascaraOsCnpjs`, `CnpjPropriedadesTest` |
| 14. Sem SEFAZ real | sem mudança: só WireMock (`NfeNacionalAdapterTest.buscarLoteComCnpjAlfanumerico`, `NfeNacionalAdapterContratoAlfanumericoTest`) |

## Decisões do agente nesta subetapa
- **DA-37:** o `Cnpj` só aceita o valor canônico (maiúsculo, sem máscara). As 26 letras são aceitas, com `LETRAS_VEDADAS` vazia. Só os 14 zeros são recusados, então `00000000000191` é válido.
- **DA-38:** o DV da chave é obrigatório só na saída (`ChaveAcessoNfe`, `IdEvento`). Na entrada (`ChaveAcesso`, extrator), só o formato é conferido.
- **DA-39:** renomeação de um helper privado em `CatalogoRlsTest` para compilar a `main`. Ela já está resolvida na base e não aparece neste diff.

## O que NÃO foi feito
- O processor de roteamento (E3-S07) ainda precisa usar `Cnpj.valido` em vez de `new Cnpj`, para não derrubar o lote.
- A porta push ainda não exige o DV da chave: isso fica para a Fase 5, com `temDvValido()`.
- `ChaveAcesso` e `ChaveAcessoNfe` não foram unificados. A entrada do cadastro não normaliza minúsculas nem máscara, porque ainda não há API. As combinações proibidas pela Receita não são bloqueadas.
- CT-e e os demais DF-e ficaram de fora, assim como o código de barras do DANFE.
- CLAUDE.md §3 e ARCHITECTURE não foram atualizados, porque são protegidos. Há um item na PARA-VOCE.
- Não houve smoke real em homologação com CNPJ alfanumérico (fica para a E2-S12).
- A NT Conjunta 2025.001 foi lida numa cópia de terceiros, a v1.00. Uma versão posterior não foi verificada (`docs/FONTES-OFICIAIS.md`).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
