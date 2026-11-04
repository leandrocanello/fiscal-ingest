-- RLS, append-only e grants do catálogo (ADR-013, §13, DA-27). Política igual à da V5 da E3-S03:
-- o tenant vem de app.tenant_id, local à transação; sem tenant, ou com valor vazio, zero linhas (falha fechada).

alter table documento enable row level security;
alter table documento force row level security;
create policy documento_isolamento on documento
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

alter table documento_empresa enable row level security;
alter table documento_empresa force row level security;
create policy documento_empresa_isolamento on documento_empresa
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

alter table arquivo enable row level security;
alter table arquivo force row level security;
create policy arquivo_isolamento on arquivo
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

alter table proveniencia enable row level security;
alter table proveniencia force row level security;
create policy proveniencia_isolamento on proveniencia
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

-- Invariantes 6 e 7: TRUNCATE fica fora do trigger, como na V2; o papel da aplicação não tem esse grant.
create trigger arquivo_append_only
    before update or delete on arquivo
    for each row execute function recusar_alteracao_ledger();

create trigger proveniencia_append_only
    before update or delete on proveniencia
    for each row execute function recusar_alteracao_ledger();

-- Condicional: o papel fiscal_app nasce na V4 da E3-S03, ainda fora da main.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'fiscal_app') then
        grant select, insert, update on documento to fiscal_app;
        grant select, insert on arquivo, documento_empresa, proveniencia to fiscal_app;
    end if;
end
$$;
