-- Quarentena (CLAUDE.md §7.3, §8): arquivo guardado sem empresa do tenant; nunca descartado.
-- Fica aberta enquanto resolvido_em é nulo; resolver é a única alteração permitida (inv. 6).
-- RLS igual à V9; grants condicionais como na V9 (fiscal_app nasce na V4 da E3-S03).

create table quarentena (
    id           bigint generated always as identity primary key,
    tenant_id    bigint      not null references tenant (id),
    arquivo_id   bigint      not null,
    motivo       text        not null check (motivo in ('SEM_EMPRESA')),
    criado_em    timestamptz not null,
    resolvido_em timestamptz,
    foreign key (tenant_id, arquivo_id) references arquivo (tenant_id, id)
);

-- No máximo uma aberta por arquivo; depois de resolvida, pode reabrir (nova linha).
create unique index quarentena_aberta_por_arquivo on quarentena (tenant_id, arquivo_id) where resolvido_em is null;

alter table quarentena enable row level security;
alter table quarentena force row level security;
create policy quarentena_isolamento on quarentena
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);

-- Vale também para superuser: só preenche resolvido_em, uma vez; nunca apaga.
create function quarentena_so_resolve() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'quarentena é append-only: delete recusado';
    end if;
    if old.resolvido_em is not null or new.resolvido_em is null
            or (new.id, new.tenant_id, new.arquivo_id, new.motivo, new.criado_em)
               is distinct from (old.id, old.tenant_id, old.arquivo_id, old.motivo, old.criado_em) then
        raise exception 'quarentena: só resolvido_em pode ser preenchido, e uma vez';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger quarentena_so_resolve
    before update or delete on quarentena
    for each row execute function quarentena_so_resolve();

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'fiscal_app') then
        grant select, insert on quarentena to fiscal_app;
        grant update (resolvido_em) on quarentena to fiscal_app;
    end if;
end
$$;
