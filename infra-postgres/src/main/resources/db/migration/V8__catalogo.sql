-- Catálogo (CLAUDE.md §8, ADR-018, DA-27): um documento por (tenant, chave), N arquivos, N proveniências.
-- Número 8 porque V4/V5 são da E3-S03 e V6/V7, da E3-S04 (DA-25).
-- tenant_id nas quatro tabelas, com FK composta: o banco recusa vínculo entre tenants.
-- RLS, append-only e grants ficam na V9.

alter table empresa add unique (tenant_id, id);

create table documento (
    id               bigint generated always as identity primary key,
    tenant_id        bigint      not null references tenant (id),
    chave_acesso     char(44)    not null check (chave_acesso ~ '^[0-9]{44}$'),
    modelo           char(2)     not null,
    serie            integer     check (serie >= 0),
    numero           bigint      check (numero >= 0),
    emissao          timestamptz,
    emitente_cnpj    char(14),
    destinatario_doc text        check (destinatario_doc ~ '^([0-9]{11}|[0-9]{14})$'),
    valor_total      numeric     check (valor_total >= 0),
    situacao         text        not null check (situacao in ('SO_RESUMO', 'AUTORIZADA', 'CANCELADA', 'DENEGADA')),
    so_declarado     boolean     not null,
    versao_layout    text,
    atualizado_em    timestamptz not null,
    unique (tenant_id, chave_acesso),
    unique (tenant_id, id)
);

create table documento_empresa (
    tenant_id    bigint not null,
    documento_id bigint not null,
    empresa_id   bigint not null,
    direcao      text   not null check (direcao in ('ENTRADA', 'SAIDA')),
    primary key (documento_id, empresa_id, direcao),
    foreign key (tenant_id, documento_id) references documento (tenant_id, id),
    foreign key (tenant_id, empresa_id) references empresa (tenant_id, id)
);

create index documento_empresa_tenant_empresa_idx on documento_empresa (tenant_id, empresa_id);

create table arquivo (
    id           bigint generated always as identity primary key,
    tenant_id    bigint      not null,
    documento_id bigint      not null,
    tipo         text        not null check (tipo in ('RESUMO', 'COMPLETO', 'EVENTO', 'RESUMO_EVENTO')),
    evento_tipo  text,
    evento_seq   integer,
    blob_ref     text        not null,
    sha256       char(64)    not null check (sha256 ~ '^[0-9a-f]{64}$'),
    recebido_em  timestamptz not null,
    unique (documento_id, sha256),
    unique (tenant_id, id),
    foreign key (tenant_id, documento_id) references documento (tenant_id, id)
);

-- recebimento_id sem FK até a tabela recebimento existir (E5-S02).
create table proveniencia (
    id             bigint generated always as identity primary key,
    tenant_id      bigint      not null,
    arquivo_id     bigint      not null,
    origem         text        not null check (origem in ('DFE_AN', 'AGENTE', 'EMAIL', 'UPLOAD')),
    confianca      text        not null check (confianca in (
        'OFICIAL', 'CONFIRMADO_SEFAZ', 'VALIDADO_ASSINATURA', 'DECLARADO')),
    ledger_nsu_id  bigint      references ledger_nsu (id),
    recebimento_id bigint,
    recebido_em    timestamptz not null,
    foreign key (tenant_id, arquivo_id) references arquivo (tenant_id, id),
    constraint proveniencia_oficial_so_dfe_an
        check ((origem = 'DFE_AN') = (confianca = 'OFICIAL')),
    constraint proveniencia_dfe_an_exige_ledger
        check (origem <> 'DFE_AN' or (ledger_nsu_id is not null and recebimento_id is null)),
    constraint proveniencia_push_exige_recebimento
        check (origem = 'DFE_AN' or (recebimento_id is not null and ledger_nsu_id is null)),
    constraint proveniencia_sem_repeticao
        unique nulls not distinct (arquivo_id, origem, confianca, ledger_nsu_id, recebimento_id)
);
