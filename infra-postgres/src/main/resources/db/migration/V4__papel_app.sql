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

-- Privilégios mínimos. Ledger só SELECT/INSERT (invariante 6: nem UPDATE/DELETE nem TRUNCATE, que o
-- trigger da V2 não cobre). estado_coleta muda só por CAS (UPDATE), nunca some. Nada em flyway_schema_history.
grant select, insert on ledger_nsu, ledger_tentativa to fiscal_app;
grant select, insert, update on estado_coleta to fiscal_app;
grant select on tenant to fiscal_app;
grant select, insert, update on empresa to fiscal_app;
