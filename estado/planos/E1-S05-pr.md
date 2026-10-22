## O que muda
- Adiciona o jqwik 1.9.3 como `testImplementation` para todos os módulos (bloco `subprojects`, ADR-026 / DA-2).
- Cria testes de propriedade para o que já existia: `Nsu` (ordem total, `proximo`, overflow), `GapCalculator` (gaps = intervalo − conhecidos, sem depender de ordem, duplicata ou status) e `TokenBucket` (teto por janela, `retryApos` honesto).
- Única mudança de produção: `TokenBucket` passa a rejeitar intervalo de recarga < 1 ms no construtor. Antes, dava `ArithmeticException` mais tarde (DA-3).
- Suíte verde: `domain` 54 (38 antigos + 16 novos), `adapter-fake` 5, `adapter-nfe-nacional` 15. Cada propriedade rodou 1000 tentativas.

## Olhar com atenção
- `build.gradle.kts:34` — o jqwik traz `junit-bom:5.13.1`, e o Gradle sobe o JUnit Jupiter de 5.10.2 para 5.13.1 (Platform 1.13.1) em todos os módulos. As versões ficam alinhadas e tudo passa, mas o `5.10.2` declarado nas linhas acima deixou de ser a versão usada. Decida se prefere declarar o `junit-bom` explicitamente.
- `domain/src/main/java/br/com/doc/ingest/domain/ratelimit/TokenBucket.java:46` — é a única mudança de comportamento de produção. Ela barra < 1 ms, mas uma fração de ms (ex.: 1,5 ms) continua truncada para 1 ms pelas linhas 92/107. Isso é inofensivo para os limites reais da SEFAZ.
- `domain/src/test/java/br/com/doc/ingest/domain/ratelimit/TokenBucketPropriedadesTest.java:50` — o limite usado (`capacidade + floor(tempo/intervalo)`) é a formalização do "nunca libera além do teto". Confira se é essa a regra que você quer congelar.
- `domain/src/test/java/br/com/doc/ingest/domain/ratelimit/TokenBucketPropriedadesTest.java:74` — o teste de `retryApos` avança exatamente o tempo sugerido e exige que a próxima tentativa seja aceita. Isso trava a semântica "retryApos é suficiente, não só uma estimativa".
- `domain/src/test/java/br/com/doc/ingest/domain/model/NsuPropriedadesTest.java:67` — documenta que `proximo()` em `Long.MAX_VALUE` lança `IllegalArgumentException`. Hoje isso funciona *por acidente* (overflow → negativo → validação do construtor), e o teste protege esse comportamento.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 1 — Não-retrocesso de NSU (base: ordem e `proximo` do `Nsu`) | `NsuPropriedadesTest` |
| §3.2 — Limite de consultas por CNPJ (relacionada à inv. 2) | `TokenBucketPropriedadesTest.nuncaLiberaMaisQueCapacidadeMaisRecargas`, `retryAposEhHonesto` |
| ADR-005 — ledger/gaps | `GapCalculatorPropriedadesTest` |

## Decisões do agente nesta subetapa
- DA-2: jqwik no `subprojects` do `build.gradle.kts` raiz (vale para todos os módulos e evita conflito com a E1-S04).
- DA-3: `TokenBucket` rejeita intervalo de recarga < 1 ms no construtor.

## O que NÃO foi feito
- A guarda e a propriedade de não-retrocesso no `AvancarPonteiroUseCase`: ficam para a E1-S06.
- A inv. 2 propriamente dita (exclusão mútua por `(fonte, cnpj, ambiente)`) continua sem teste. O `TokenBucket` não é thread-safe e só cobre o ritmo. A exclusão mútua depende de lease e CAS (Fases 3/4).
- Bugs conhecidos que ficaram fora do escopo, sem correção: `GapCalculator` entra em laço infinito se o maior NSU for `Long.MAX_VALUE` e estoura memória com faixa enorme; `TokenBucket` com relógio que volta no tempo; a recarga descarta a fração do intervalo ao consumir (é conservador e não viola o limite).
- O `junit-platform.properties` opcional para mover o `.jqwik-database` para `build/` não foi criado. O arquivo foi para o `.gitignore`.
- O `junit-bom` não foi declarado explicitamente (veja "Olhar com atenção").

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
