-- Outbox transacional (ADR-015): evento gravado na mesma transação do estado; relay publica depois.
-- Número 6 porque V4/V5 são reservadas à E3-S03 (DA-25).

create table outbox (
    id                 bigint generated always as identity primary key,
    topico             text        not null,
    chave              text        not null,
    versao_schema      int         not null check (versao_schema >= 1),
    chave_idempotencia text        not null unique,
    payload            jsonb       not null,
    criado_em          timestamptz not null,
    publicado_em       timestamptz
);

create index outbox_pendentes_idx on outbox (topico, chave, id) where publicado_em is null;

-- Mensagem não é reescrita: só publicado_em pode ir de nulo para um valor; DELETE recusado.
create function recusar_alteracao_outbox() returns trigger as $$
begin
    if tg_op = 'UPDATE'
       and old.publicado_em is null
       and new.publicado_em is not null
       and new.id = old.id
       and new.topico = old.topico
       and new.chave = old.chave
       and new.versao_schema = old.versao_schema
       and new.chave_idempotencia = old.chave_idempotencia
       and new.payload = old.payload
       and new.criado_em = old.criado_em then
        return new;
    end if;
    raise exception 'outbox: % não permitido (só publicado_em de nulo para valor)', tg_op;
end;
$$ language plpgsql;

create trigger outbox_imutavel
    before update or delete on outbox
    for each row execute function recusar_alteracao_outbox();

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'fiscal_app') then
        grant select, insert, update on outbox to fiscal_app;
    end if;
end
$$;
