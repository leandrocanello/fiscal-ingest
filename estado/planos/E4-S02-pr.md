## O que muda
Cria o módulo `cert-vault` com a porta `CofreDeCertificados` (tipos e portas em `domain.cofre`) e a implementação `CofreLocal`.
O upload de `.pfx` + senha é validado (abre, cadeia PKIX até raiz injetada, validade na data do `Clock`, CNPJ do `otherName` ICP-Brasil == titular),
cifrado em AES-256-GCM (DEK por certificado embrulhada por KEK local, AAD amarrando tenant/titular/fingerprint/parte) e guardado só como cifrado via `BlobStore` + metadados via `RepositorioCertificados`.
A abertura decifra só em memória e devolve uma `Credencial` nova; vencido, ausente, trocado ou com KEK desconhecida volta como resultado tipado, sem causa.
Suíte inteira verde (cert-vault: 117 testes; total 639, 0 falhas). Nenhum teste antigo alterado.

## Olhar com atenção
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/EnvelopeCifrado.java:224` — formato do envelope em disco (DA-35). É o que fica difícil de mudar depois do primeiro certificado real; a versão no byte 0 permite migrar, mas confira se o layout te agrada agora.
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/CofreLocal.java:85` — cada reenvio do mesmo pfx cifra de novo e grava 2 envelopes órfãos (o repositório devolve o registro antigo). Inofensivo em segurança, mas sem limite cresce o storage; precisa de rate limit no `app-api` ou checar o registro antes de cifrar.
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/CofreLocal.java:148` — chave do blob `cofre/{tenant}/{fingerprint}/...` não inclui o titular, mas a identidade do repositório (`RepositorioCertificadosEmMemoria.java:12`) inclui. Sem colisão real (sha256 + AAD), mas alinhar quando a tabela `certificado` nascer (E4-S02b).
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/CofreLocal.java:108` — `abrir` só checa `validoAte`; o "ainda não iniciado" não é reavaliado (o upload já recusa). Quem transforma `Vencido` em `CERT_VENCIDO` é a E4-S03/S04.
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/ExtratorCnpjCertificado.java:23` — OID `2.16.76.1.3.3` vem de memória (DOC-ICP-04), marcado [VALIDAR]. Falha fechada (`SEM_CNPJ`). Linha 88 aceita só dígitos: certificado com **CNPJ alfanumérico** seria recusado — conflita com o `Cnpj` alfanumérico da E1-S07 (PARA-VOCE `[DUVIDA] E4-S02`).
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/ValidadorPfx.java:477` — igualdade exata dos 14 dígitos: filial com certificado da matriz é recusada (DA-36). Decisão de produto que vale confirmar.
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/ValidadorPfx.java:494` — revogação desligada no upload (DA-36); um revogado é aceito e só falha no mTLS com a SEFAZ.
- `cert-vault/src/main/java/br/com/doc/ingest/certvault/ValidadorPfx.java:459` — `catch (Exception)` amplo, proposital para não vazar causa (inv. 13). O custo de PBKDF do PKCS#12 é escolhido pelo arquivo enviado; o JDK tem teto de iterações, mas vale medir com upload real.
- `cert-vault/build.gradle.kts:18` — `adapter-nfe-nacional` como `testImplementation`, só para provar que a credencial aberta carrega no `CarregadorPfx` real. Produção continua dependendo só de `domain`.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 5 — grave primeiro, avance depois | `CofreLocalGuardarTest.falhaNoBlobStoreNaoRegistraMetadado`, `recusaNaoGravaNada` |
| 7 — blob imutável | `CofreLocalGuardarTest.chaveDoBlobContemOSha256DoCifrado`, `reenvioDoMesmoPfxNaoCriaOutroRegistro`, `reenvioConcorrenteDevolveOMesmoId` |
| 13 — nada de segredo em log/exceção/disco | `CofreSemSegredoTest` (blobs, registro, `toString`, causas, disco; mutação vista e revertida), `ArquiteturaCofreTest.semLogNoCofre`/`semEscritaEmDisco`, `EnvelopeCifradoPropriedadesTest.cifradoNuncaContemOConteudoClaro`, `PedidoGuardaTest.toStringNaoMostraPfxNemSenha` |
| 14 — nunca SEFAZ real | `ArquiteturaCofreTest.semHttpNemSocket`; só certificados sintéticos gerados em memória (`GeradorCertificadoDeTeste`) |
| 9 (parcial) — entrada não confiável | `ValidadorPfxTest.recusaAcimaDoLimiteAntesDeAbrir`, `ExtratorCnpjCertificadoTest.bytesAleatoriosNuncaLancam` |

## Decisões do agente nesta subetapa
- DA-35: AES-256-GCM do JDK; dois envelopes autocontidos via `BlobStore`; AAD `tenant|titular|fingerprint|parte`; `KeyStore` próprio no `cert-vault` (≈30 linhas duplicadas do `CarregadorPfx`) para manter a regra de dependência da §19.
- DA-36: CNPJ só do `otherName` (OID [VALIDAR]), leitor DER próprio, revogação desligada, igualdade exata dos 14 dígitos, PKIX com data do `Clock`.

## O que NÃO foi feito
- Tabela `certificado`, repositório JDBI e RLS — dependem da E3-S03 (sugerida E4-S02b). Hoje só há repositório em memória: nada persiste entre execuções.
- `BlobStore` real (E3-S05), composição nos `app-*`, leitura da KEK do ambiente e upload HTTP.
- Acervo real de ACs ICP-Brasil: `ConfiancaIcp` é injetada; nenhum teste com certificado ICP real (conferência manual do autor, E2-S12).
- Alertas de vencimento, rotação, escolha do certificado vigente e invalidação do `CacheSslContext` (E4-S04); consentimento e auditoria de uso do cofre (E4-S03).
- Regravar envelopes com KEK nova, Vault/KMS, LCR/OCSP, `keyUsage`/`extendedKeyUsage`.
- CNPJ alfanumérico no certificado e filial com certificado da matriz — em aberto no PARA-VOCE.
- Zeragem total de segredo em memória: o JDK guarda cópias não zeráveis (`SecretKeySpec`, `KeyStore`); está documentado no Javadoc, sem prometer mais.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
