## O que muda
- Fecha a auditoria A6: `Credencial` passa a ser `byte[] pfx` + `char[] senha`, só em memória (cópias defensivas, `limpar()`, `toString` sem segredo); saem `tipo` e `caminho`.
- Novo pacote `adapter-nfe-nacional/.../cert`: `CarregadorPfx` (PKCS#12 moderno e legado RC2/3DES — o JDK 21 abre os dois, sem BouncyCastle em produção), erros tipados `CertificadoInvalidoException`/`MotivoCertificadoInvalido`.
- `FabricaSslContext`: `SSLContext` mTLS (TLS 1.3/1.2) com o certificado do titular e truststore injetado.
- `CacheSslContext`: LRU + TTL com `Clock` injetado, chave = SHA-256 do pfx, carga única sob concorrência, `invalidar(fingerprint)`.
- BouncyCastle (`bcpkix`) só em `testImplementation`, para gerar pfx sintéticos em memória.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/cert/CacheSslContext.java:63` — a chave é o fingerprint dos bytes, não a senha: num acerto de cache, uma senha errada não é verificada. Confirme se aceita isso (credencial vem sempre do cofre) ou se quer comparar um hash da senha.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/cert/CacheSslContext.java:69` — o TTL não considera `validoAte` do certificado; quem garante parar no vencimento é o cofre (E4), que deve chamar `invalidar`.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/cert/CacheSslContext.java:81-91` — sincronização (futuro por chave + lock curto no LRU); a falha remove a entrada e se propaga a todos que esperavam. Coberta por teste com 50 virtual threads e propriedade jqwik.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/cert/CarregadorPfx.java:46` — `catch (Exception)` vira `ILEGIVEL` sem causa (de propósito, inv. 13); dificulta diagnóstico. Avalie se quer registrar ao menos o nome da classe da causa, como faz `FabricaSslContext`.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/cert/FabricaSslContext.java:23` — `TLSv1.3` como protocolo do contexto (negocia 1.2 também). Confirmar no smoke de homologação (E2-S12).
- `domain/src/main/java/br/com/doc/ingest/domain/adapter/Credencial.java:57` — `hashCode` derivado da senha e `equals` sem tempo constante; risco baixo, mas pode ser removido.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 3/4 — domínio puro, transporte fora do core | `ArquiteturaDominioTest` (verde com a nova `Credencial`) |
| 13 — nada de segredo em log/exceção | `CredencialTest.toStringNaoMostraBytesNemSenha`, `CarregadorPfxTest.senhaErradaNaoApareceNaMensagem`, `CertificadoCarregadoTest.toStringSoMostraFingerprintEValidade` |
| 14 — nunca SEFAZ real | `FabricaSslContextTest` (WireMock HTTPS em localhost, keystores em `@TempDir`) |

## Decisões do agente nesta subetapa
- DA-9: BouncyCastle só para gerar pfx nos testes (bcprov em produção só se o JDK recusasse o legado — não recusou); cache LRU+TTL próprio em vez de Caffeine; pfx com mais de uma chave privada = `AMBIGUO`.

## O que NÃO foi feito
- Validação da cadeia ICP-Brasil, do CNPJ do certificado e do titular → cofre (E4-S02).
- Truststore real com as ACs ICP-Brasil → DUVIDA E2-S10 em PARA-VOCE / smoke E2-S12.
- Ligar o `SSLContext` ao transporte e ao `autenticar()` do adapter real → E2-S07.
- `RelogioManual` foi duplicado no módulo do adapter (já existe no `domain` de teste); test fixtures compartilhadas ficam para depois.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
