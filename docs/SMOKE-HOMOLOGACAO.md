# Smoke manual em homologação (E2-S12 / E2-S17)

Nunca roda no CI (inv. 14). Só ambiente de **homologação**; não existe variável para produção.

```
FISCAL_SMOKE_PFX=/caminho/cert.pfx FISCAL_SMOKE_SENHA=... FISCAL_SMOKE_CNPJ=... \
FISCAL_SMOKE_URL_DIST=https://... \
./gradlew :adapter-nfe-nacional:smokeHomologacao -Dsmoke.homologacao=true
```

Sem `-Dsmoke.homologacao=true` tudo fica *skipped*.

## Variáveis
| Variável | Obrigatória | Nota |
|---|---|---|
| `FISCAL_SMOKE_PFX` | sim | caminho do `.pfx` |
| `FISCAL_SMOKE_SENHA` | sim | nunca vai a log |
| `FISCAL_SMOKE_CNPJ` | sim | CNPJ do titular |
| `FISCAL_SMOKE_URL_DIST` | sim | `https` |
| `FISCAL_SMOKE_URL_EVENTO` | não | necessário para a ciência |
| `FISCAL_SMOKE_CHAVE` | não | habilita `consChNFe` e ciência |
| `FISCAL_SMOKE_MANIFESTAR` | não | `true` para enviar a ciência (padrão `false`) |
| `FISCAL_SMOKE_DIR` | não | padrão `~/fiscal-smoke` |
| `FISCAL_TRUSTSTORE` | não | sobrescreve o truststore ICP-Brasil do classpath |

## Onde ficam os dados
- Cursor: `<dir>/cursor-<cnpj>.txt` (recusa NSU menor que o salvo).
- Brutos: `<dir>/AAAA-MM-DD/...` (a pasta não pode estar dentro de um repositório git).

**Não commitar nada de `~/fiscal-smoke`.** Roteiro completo: item `[HUMANO] E2-S12` do PARA-VOCE.md.
