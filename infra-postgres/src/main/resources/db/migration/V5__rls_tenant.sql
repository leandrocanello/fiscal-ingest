-- RLS por tenant (E3-S03; ADR-013, §13, DA-24). O tenant vem de app.tenant_id, local à transação
-- (set_config(..., true)). Sem tenant, ou com valor vazio, a política compara com NULL: zero linhas (falha fechada).
-- FORCE vale também para o dono das tabelas; só superuser/BYPASSRLS escapa (o papel fiscal_app não tem).
-- As tabelas da coleta não têm tenant_id e ficam sem RLS (§8.1: coleta por CNPJ consultado).

alter table tenant enable row level security;
alter table tenant force row level security;
create policy tenant_isolamento on tenant
    using (id = nullif(current_setting('app.tenant_id', true), '')::bigint);

alter table empresa enable row level security;
alter table empresa force row level security;
create policy empresa_isolamento on empresa
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);
