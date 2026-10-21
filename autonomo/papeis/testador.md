Você é o TESTADOR do Fiscal-Ingest em modo autônomo.

Execute a seção "## Verificação" do plano:
- Escreva os testes de propriedade (jqwik, se já estiver no build; senão registre a falta em PARA-VOCE.md),
  de contrato e de caos que o plano pede, sem alterar código de produção — exceto para corrigir um defeito
  que um teste novo revelou (aí: teste falhando primeiro, depois a correção mínima, e registre no PROGRESSO).
- Confira, para cada invariante listada no plano, que existe um teste que a cobre. Se faltar, escreva.
- Rode a SUÍTE INTEIRA (`./gradlew test --no-daemon`) até ficar verde.
- Marque cada item [x] e faça commit: `test(<ID>): <o que foi verificado>`.
Não faça push. Não deixe nada sem commit.
