## O que muda
O `CStatMapper` passa a seguir a decisão E2-S02 (MOC 7.0) e fecha os achados A1–A3 da auditoria 1.5:
- `108` → `IndisponivelTemporario(5 min)` (antes era `Ok`)
- `632` → `IndisponivelDefinitivo("fora de prazo")` (antes era `LimiteAtingido`)
- `589` → `IndisponivelDefinitivo("cursor inconsistente")` (antes caía no fallback)
- `109` → `ServicoFora` com motivo "sem previsão"
- `656` fixado em `LimiteAtingido(1h)`

O fallback (código desconhecido, nulo ou vazio) agora emite um WARN só com o código sanitizado. O `slf4j-api` entra no adapter e o `logback-classic` só em teste. Há também novas propriedades jqwik.

## Olhar com atenção
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapper.java:61` — agora só `138` vira `Ok`. É o ponto mais sensível para a inv. 1: o 108 como `Ok` fazia o loop tratar uma paralisação como sucesso.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapper.java:70` — com `632 → IndisponivelDefinitivo`, o §6.7 leva o **CNPJ inteiro** a `ERRO_DEFINITIVO`. Como o 632 só ocorre em consChNFe (falha de um documento), a E2-S09/E4 precisa diferenciar o tipo de consulta antes de usar o mapper ali. Confirme se concorda.
- `adapter-nfe-nacional/src/main/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapper.java:87` e `:96` — o log do fallback (inv. 13). Ele aceita só `[A-Za-z0-9]` com até 16 chars e não tem limite de taxa: um cStat novo em massa vira um WARN por chamada.
- `adapter-nfe-nacional/build.gradle.kts:17` e `:23` — a primeira dependência de log num módulo de biblioteca (DA-8). Avalie se prefere uma porta de observação em vez disso.
- `adapter-nfe-nacional/src/test/java/br/com/doc/ingest/adapter/nfe/mapping/CStatMapperPropriedadesTest.java:58` — o gerador mistura strings livres, numéricas de 3 dígitos e os próprios códigos. Confira se cobre o que você espera.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — não-retrocesso de NSU | `CStatMapperPropriedadesTest.soCodigosDeSucessoViramOkOuVazio`, `CStatMapperTest.cStat108NaoEhSucesso`, `cStat632NaoEhLimiteAtingido` |
| 4 — core desacoplado | regras ArchUnit existentes (E1-S04). O diff em `domain/` é só Javadoc |
| 13 — nada de segredo/XML em log | `CStatMapperTest.fallbackLogaSoOCStatBruto`, `fallbackTruncaESanitizaEntradaLonga`, `fallbackNuloOuVazioTambemLoga` |

## Decisões do agente nesta subetapa
- DA-8: `slf4j-api` no adapter e `logback-classic` só em teste, para o WARN do fallback (ADR-028).

## O que NÃO foi feito
- O `CStatMapper` ainda não é usado no `buscarLote` (E2-S07), e o loop/scheduler ainda não trata `IndisponivelDefinitivo` (E4).
- O 632 não é diferenciado por tipo de consulta. O contexto de consulta entra na E2-S09.
- `107`, `593`, `472`, `641` e as demais rejeições do MOC continuam caindo no fallback.
- `docs/ARCHITECTURE.md` §5.3 e `CLAUDE.md` §3.2 continuam com o mapeamento antigo. São arquivos protegidos; o pedido está em PARA-VOCE.md ([DUVIDA] E2-S03).
- Não houve smoke em homologação: a subetapa é só de mapeamento.

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
