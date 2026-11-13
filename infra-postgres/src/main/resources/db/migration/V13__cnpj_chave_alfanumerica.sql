-- CNPJ e chave de acesso alfanuméricos (E1-S07, NT Conjunta 2025.001; DA-37, DA-38).
-- Número 13 porque V10–V12 são da E3-S07 e da E4-S01.
-- Só formato: o DV é conferido no domínio (Cnpj, ChaveAcesso), não no banco.
-- CNPJ: raiz+ordem [A-Z0-9]{12}, DV [0-9]{2}. Chave: cUF+AAMM [0-9]{6}, CNPJ base [A-Z0-9]{12}, resto [0-9]{26}.

alter table empresa
    add constraint empresa_cnpj_formato check (cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    add constraint empresa_raiz_cnpj_formato check (raiz_cnpj ~ '^[A-Z0-9]{8}$');

alter table estado_coleta
    add constraint estado_coleta_cnpj_consultado_formato check (cnpj_consultado ~ '^[A-Z0-9]{12}[0-9]{2}$');

alter table ledger_tentativa
    add constraint ledger_tentativa_cnpj_consultado_formato check (cnpj_consultado ~ '^[A-Z0-9]{12}[0-9]{2}$');

alter table ledger_nsu
    add constraint ledger_nsu_cnpj_consultado_formato check (cnpj_consultado ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    add constraint ledger_nsu_chave_acesso_formato check (chave_acesso ~ '^[0-9]{6}[A-Z0-9]{12}[0-9]{26}$');

-- Troca os CHECKs da V8 (só numéricos), mantendo os nomes. destinatario_doc: CPF (11 dígitos) ou CNPJ.
alter table documento
    drop constraint documento_chave_acesso_check,
    add constraint documento_chave_acesso_check check (chave_acesso ~ '^[0-9]{6}[A-Z0-9]{12}[0-9]{26}$'),
    drop constraint documento_destinatario_doc_check,
    add constraint documento_destinatario_doc_check
        check (destinatario_doc ~ '^([0-9]{11}|[A-Z0-9]{12}[0-9]{2})$'),
    add constraint documento_emitente_cnpj_formato check (emitente_cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$');
