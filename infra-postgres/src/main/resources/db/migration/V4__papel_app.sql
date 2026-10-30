-- Papel da aplicação (E3-S03): sem superuser e sem BYPASSRLS, para o RLS por tenant valer (ADR-013, §13).
-- É um papel de grupo (nologin); os logins de cada ambiente herdam dele. Papéis são globais no cluster,
-- então a criação é idempotente: aplicar as migrations em outro schema não falha.

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'fiscal_app') then
        create role fiscal_app nologin nosuperuser nobypassrls;
    end if;
    execute format('grant usage on schema %I to fiscal_app', current_schema());
end;
$$;
