-- Tenants de um CNPJ consultado (E3-S07, D3 (a)): a coleta não tem tenant (§8.1), mas o processor precisa
-- saber a quais tenants a nota vai antes de ter um. A empresa terá RLS forçada (V5 da E3-S03), e o FORCE vale
-- até para o dono da tabela; por isso a leitura passa por um papel próprio, sem login, que só vê as colunas
-- necessárias, e por uma função security definer dele que devolve só ids de tenant (privilégio mínimo, §13).
-- Papéis são globais no cluster: a criação é idempotente, como na V4 da E3-S03.

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'fiscal_roteador') then
        create role fiscal_roteador nologin nosuperuser nobypassrls;
    end if;
    execute format('grant usage on schema %I to fiscal_roteador', current_schema());
end;
$$;

grant select (tenant_id, cnpj, ambiente, ativo) on empresa to fiscal_roteador;

-- Permissiva e só para esse papel: soma-se à política por tenant da V5 sem afrouxá-la para os demais.
create policy empresa_leitura_roteador on empresa for select to fiscal_roteador using (true);

-- search_path fixo (com o schema desta migração) contra sequestro de nomes numa função security definer.
do $$
begin
    execute format($f$
        create function tenants_do_cnpj_consultado(p_cnpj text, p_ambiente text) returns setof bigint
        language sql stable security definer set search_path = %I, pg_temp
        as 'select distinct tenant_id from empresa
            where cnpj = p_cnpj and ambiente = p_ambiente and ativo
            order by tenant_id'
    $f$, current_schema());
end;
$$;

alter function tenants_do_cnpj_consultado(text, text) owner to fiscal_roteador;
revoke execute on function tenants_do_cnpj_consultado(text, text) from public;

-- Condicional: o papel fiscal_app nasce na V4 da E3-S03, ainda fora da main.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'fiscal_app') then
        grant execute on function tenants_do_cnpj_consultado(text, text) to fiscal_app;
    end if;
end
$$;
