# E2-S01 — docs/FONTES-OFICIAIS.md (rascunho de fontes dos [VALIDAR])

## O que muda
- Cria `docs/FONTES-OFICIAIS.md` com o índice dos 14 itens da CLAUDE.md §21, a tabela de rastreio de cada [VALIDAR] (CLAUDE.md, ARCHITECTURE l.41/43/178, AUDITORIA A1/A2/A3/A5) e uma seção por item (Pergunta/Fonte/Trecho/Leitura/Status/Afeta).
- **Só há fontes secundárias.** Nenhuma NT/MOC primária foi lida: o portal nfe.fazenda.gov.br deu "Too many redirects" e os PDFs de terceiros não foram extraídos. Nenhum item está conferido.
- Nenhum código, teste ou outro doc foi alterado.

## Olhar com atenção
- `docs/FONTES-OFICIAIS.md:91` — item 3 (`cStat`): só o `589` e o `656` têm fonte. `108` e `632`, que são os achados A1/A2 e travam a E2-S02, continuam **sem fonte**. É o primeiro PDF que vale abrir à mão: a NT 2014.002 v1.40 citada na l.130.
- `docs/FONTES-OFICIAIS.md:184` — único ⚠: indício de guarda de 132 meses (Ajuste SINIEF 2/2025) contra os 5 anos do §14. Já está em PARA-VOCE como [DUVIDA].
- `docs/FONTES-OFICIAIS.md:107` — limite do `autXML` com fontes em conflito (9 × 10). Afeta o canal B/ADR-019.
- `docs/FONTES-OFICIAIS.md:139` — única URL de webservice (homologação, fórum de 2014), anotada como texto "NÃO ACESSAR". Confira se ela não deve ficar fora do doc até ser confirmada no portal.
- Itens 4, 5, 10–14 (l.98, 106, 146–182): muitos links estão como "resultado de busca, página não aberta". Nesses casos o "trecho" é paráfrase do buscador, não citação da página. Trate como pista.
- `docs/FONTES-OFICIAIS.md:91` e `:124` — os textos de 107/108/109 e o formato do `Id` do evento vêm da memória do agente. Estão marcados "não usar", mas não os copie para teste.

## Invariantes tocadas
| Invariante (CLAUDE.md §2.2) | Teste que cobre |
|---|---|
| 14 (nunca chamar a SEFAZ real) | Sem código. Nenhuma URL de webservice foi acessada; a única aparece como texto no item 9 |
| 1 (não-retrocesso) | Só indireta (fonte do `656`/`589`). Nenhum teste mudou; o `CStatMapperTest` atual segue verde |

## Decisões do agente nesta subetapa
- Nenhuma em DECISOES-AGENTE.md. Uma [DUVIDA] em PARA-VOCE (prazo de guarda, item 14).

## O que NÃO foi feito
- Nenhuma fonte primária (NT 2014.002, NT 2020.001, MOC, Ajuste SINIEF) foi lida. É preciso abrir os PDFs à mão (o ambiente não tem `pdftotext`).
- Nenhum item foi marcado como conferido; isso cabe ao autor.
- CLAUDE.md, ARCHITECTURE e DECISIONS não foram editados (fora do escopo).
- O commit `a14d126` é vazio: registra a verificação (`./gradlew test` verde, 17 [VALIDAR] cobertos).

---
Para pedir mudanças: comente (no código ou aqui) e adicione o rótulo `pedir-mudancas`.
Para aprovar: faça o merge.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
