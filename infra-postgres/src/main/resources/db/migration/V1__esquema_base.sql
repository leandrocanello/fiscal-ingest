-- Esquema base (CLAUDE.md §8, blocos "Cadastro" e "Coleta pull").
-- Ids de tenant/empresa: bigint identity (simples; troca por uuid só se a medição pedir).
-- CNPJ sempre char(14), só dígitos.

create table tenant (
    id        bigint generated always as identity primary key,
    nome      text        not null,
    criado_em timestamptz not null default now(),
    ativo     boolean     not null default true
);

create table empresa (
    id           bigint generated always as identity primary key,
    tenant_id    bigint      not null references tenant (id),
    cnpj         char(14)    not null,
    raiz_cnpj    char(8)     not null,
    razao_social text        not null,
    tipo         text        not null check (tipo in ('EMPRESA', 'ESCRITORIO')),
    ambiente     text        not null check (ambiente in ('PRODUCAO', 'HOMOLOGACAO')),
    criado_em    timestamptz not null default now(),
    ativo        boolean     not null default true,
    unique (tenant_id, cnpj)
);

-- Estado quente da coleta; mutável só por CAS (versao) — lógica em E3-S02.
create table estado_coleta (
    fonte           text        not null,
    cnpj_consultado char(14)    not null,
    ambiente        text        not null check (ambiente in ('PRODUCAO', 'HOMOLOGACAO')),
    certificado_id  bigint,
    cursor_token    text,
    ult_nsu         bigint,
    max_nsu         bigint,
    versao          bigint      not null default 0,
    estado          text        not null default 'ATIVO' check (estado in (
        'ATIVO', 'BLOQUEADO_ATE', 'AGUARDANDO_CERTIFICADO', 'CERT_VENCIDO',
        'PAUSADO_PELO_CLIENTE', 'CONSENTIMENTO_REVOGADO', 'ERRO_DEFINITIVO')),
    bloqueado_ate   timestamptz,
    due_at          timestamptz,
    lease_owner     text,
    lease_ate       timestamptz,
    atualizado_em   timestamptz not null default now(),
    primary key (fonte, cnpj_consultado, ambiente)
);

create table ledger_tentativa (
    id                bigint generated always as identity primary key,
    fonte             text        not null,
    cnpj_consultado   char(14)    not null,
    ambiente          text        not null check (ambiente in ('PRODUCAO', 'HOMOLOGACAO')),
    iniciada_em       timestamptz not null,
    cursor_in         text,
    cursor_out        text,
    desfecho          text,
    cstat_bruto       text,
    qtd_docs          integer,
    duracao_ms        bigint,
    worker_id         text,
    blob_envelope_ref text
);

create table ledger_nsu (
    id              bigint generated always as identity primary key,
    fonte           text        not null,
    cnpj_consultado char(14)    not null,
    ambiente        text        not null check (ambiente in ('PRODUCAO', 'HOMOLOGACAO')),
    nsu             bigint      not null,
    status          text        not null check (status in (
        'BAIXADO', 'RESUMO', 'EVENTO', 'DESCARTADO', 'FORA_PRAZO', 'GAP')),
    schema_doc      text,
    chave_acesso    char(44),
    blob_ref        text,
    sha256          char(64),
    recebido_em     timestamptz not null,
    tentativa_id    bigint references ledger_tentativa (id),
    unique (fonte, cnpj_consultado, ambiente, nsu)
);
