# Progresso (log curto, uma linha por evento)

- 2026-10-21 21:33 · E1-S04: plano criado (4 regras ArchUnit + meta-testes com fixtures; DA-1) · próximo: desenvolver E1-S04
- 2026-10-21 21:33 · E1-S04: PLANEJAR ok
- 2026-10-21 21:45 · E1-S04: regra ArchUnit dominioSoDependeDoJdkEDeSiMesmo + meta-teste (archunit-junit5 1.5.1, testImplementation). Falha inicial: 'package com.tngtech.archunit.lang does not exist' (erro de compilação, dependência ausente); depois verde, suíte inteira verde · próximo: dominioNaoFazIo
- 2026-10-21 23:01 · E1-S04: revertido — suíte vermelha (ver logs/ultimo-teste.log)
- 2026-10-21 · E1-S04: regra dominioNaoFazIo (falhou por compilação: NAO_FAZ_IO inexistente; depois verde, suíte inteira) · próximo: dominioNaoUsaRelogioSolto
- 2026-10-21 23:08 · E1-S04: DESENVOLVER ok
- 2026-10-21 23:09 · E1-S04: regra dominioNaoUsaRelogioSolto + 2 meta-testes (falha inicial: símbolo inexistente; suíte verde) · próximo: nenhumaClasseDoDominioFalaDeCStat
- 2026-10-21 23:09 · E1-S04: DESENVOLVER ok
