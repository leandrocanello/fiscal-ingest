-- Registro de consumo idempotente do outbox (ADR-015). Append-only (trigger da V2).

create table outbox_consumo (
    consumidor         text        not null,
    chave_idempotencia text        not null,
    processado_em      timestamptz not null,
    primary key (consumidor, chave_idempotencia)
);

create trigger outbox_consumo_append_only
    before update or delete on outbox_consumo
    for each row execute function recusar_alteracao_ledger();

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'fiscal_app') then
        grant select, insert on outbox_consumo to fiscal_app;
    end if;
end
$$;
