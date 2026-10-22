# Decisões tomadas pelo agente

> Escolhas pequenas e reversíveis que o agente fez sozinho, para você revisar.
> Se discordar, responda abaixo da entrada; se a decisão for recorrente, transforme em ADR.
>
> Formato: `### DA-<n> · AAAA-MM-DD · <ID>` · Contexto · Opções · Escolha · Como desfazer

### DA-1 · 2026-10-21 · E1-S04
**Contexto:** a regra ArchUnit do domínio precisa morar em algum lugar sem sujar o `domain`.
**Opções:** (a) `testImplementation` no próprio `domain`; (b) módulo `arch-tests` separado que importa todos os módulos.
**Escolha:** (a) — o `domain` continua sem dependência de produção; um módulo separado só vale quando houver regras para vários módulos.
**Como desfazer:** mover `domain/src/test/.../arquitetura/` para um módulo novo e tirar o `testImplementation` do `domain/build.gradle.kts`.

### DA-2 · 2026-10-21 · E1-S05
**Contexto:** jqwik (ADR-026) precisa entrar no build; o PR da E1-S04 (ainda não mergeado) altera `domain/build.gradle.kts`.
**Opções:** (a) `testImplementation` no `subprojects` do `build.gradle.kts` raiz; (b) só no `domain/build.gradle.kts`.
**Escolha:** (a) — o ADR-026 vale para todos os módulos (adapters e infra também terão propriedades) e evita conflito de merge com a E1-S04. Se o JUnit Platform precisar de alinhamento, usar `junit-bom` no mesmo bloco.
**Como desfazer:** mover a linha do jqwik (e o BOM, se houver) para o `build.gradle.kts` de cada módulo.

### DA-3 · 2026-10-21 · E1-S05
**Contexto:** `TokenBucket` aceita intervalo de recarga < 1 ms, mas calcula com `toMillis()` → divisão por zero (`ArithmeticException`) no primeiro `tentarConsumir` após esgotar.
**Opções:** (a) rejeitar no construtor intervalo < 1 ms; (b) passar a calcular em nanos; (c) só restringir o gerador do teste.
**Escolha:** (a) — falha cedo e clara; nenhum limite real da SEFAZ é sub-milissegundo. Mudança de 1 linha com teste.
**Como desfazer:** remover a validação e o teste `intervaloSubMilissegundoRejeitado`; se preferir (b), trocar `toMillis` por `toNanos` nos dois cálculos.
