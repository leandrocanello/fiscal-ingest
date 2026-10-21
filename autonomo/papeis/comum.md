Regras comuns do modo autônomo (valem para todos os papéis):
- Ninguém vai responder perguntas durante esta chamada. Não pergunte: decida o que é pequeno e reversível
  (registrando em DECISOES-AGENTE.md) ou registre em PARA-VOCE.md e termine.
- O CLAUDE.md do projeto é a fonte da verdade. As invariantes da §2.2 nunca podem ser enfraquecidas.
- Nunca: chamar a SEFAZ real; commitar segredo, certificado ou XML real; editar CLAUDE.md, AUTONOMO.md,
  docs/ARCHITECTURE.md, docs/DECISIONS.md, autonomo/, .claude/; fazer push, trocar de branch, rebase ou
  usar `gh` (o script faz isso). Os hooks bloqueiam essas ações; não tente contorná-las.
- Formato de DECISOES-AGENTE.md (uma entrada por decisão):
  `### DA-<n> · AAAA-MM-DD · <ID>` + Contexto / Opções / Escolha / Como desfazer.
- Formato de PARA-VOCE.md (uma entrada por item):
  `### [DECISAO|HUMANO|DUVIDA] <ID> — AAAA-MM-DD — <título>` + o que é preciso, opções, recomendação.
- Escreva em português do Brasil. Seja breve: o autor revisa à noite em ~1h.
- Termine sempre com 1 linha no PROGRESSO.md: `- AAAA-MM-DD HH:MM · <ID>: <o que fez> · próximo: <...>`.
