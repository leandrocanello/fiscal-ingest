# ACs raiz ICP-Brasil (truststore padrão)

Origem: repositório oficial do ITI, `https://acraiz.icpbrasil.gov.br/credenciadas/RAIZ/ICP-Brasilv<N>.crt`
Baixado em: 2026-11-13. Certificados públicos; nenhum segredo.
`indice.txt` lista `<arquivo> <sha256>`; o `TruststoreIcpBrasil` recusa arquivo que não bata.

| Arquivo | sha256 | Validade |
|---|---|---|
| ICP-Brasilv10.crt | 3b3ef39649ba13e10a99bc042cfc112723d8e0503326174f15ab89941a0f33e0 |  Jul  1 19:15:59 2019 GMT → Jul  1 12:00:59 2032 GMT  |
| ICP-Brasilv11.crt | 1437394beb7eb04180a94c480319f432dc27ca9d6ebc036b73f10e2a17a7d7d8 |  Jul  1 19:26:18 2019 GMT → Jul  1 12:00:18 2032 GMT  |
| ICP-Brasilv12.crt | ce6c66c73e41b12881ea8a9b8cb7efef9a482ea012c3cd3b843667e37a7a145c |  Oct 22 14:41:24 2024 GMT → Oct 22 12:00:24 2037 GMT  |
| ICP-Brasilv5.crt | 5bd85f219695dabe6cf3d4bd713d9bd8e41b2323194022acf1acd658daef148a |  Mar  2 13:01:38 2016 GMT → Mar  2 23:59:38 2029 GMT  |

**[VALIDAR no smoke E2-S12]:** se o servidor do AN não enviar as intermediárias na cadeia, incluir as ACs
intermediárias aqui (e no `indice.txt`). Para sobrescrever sem recompilar: `FISCAL_TRUSTSTORE`.
