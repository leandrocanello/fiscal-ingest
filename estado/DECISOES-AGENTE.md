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
