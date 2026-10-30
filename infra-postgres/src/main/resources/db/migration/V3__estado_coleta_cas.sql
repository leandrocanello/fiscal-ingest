-- Invariante 1: não-retrocesso de NSU garantido também no banco (defesa em profundidade do CAS, E3-S02).
-- Cursor e ult_nsu passam a ser obrigatórios; estado novo nasce com cursor inicial ('') e NSU zero.

update estado_coleta set ult_nsu = 0 where ult_nsu is null;
update estado_coleta set cursor_token = '' where cursor_token is null;

alter table estado_coleta
    alter column ult_nsu set default 0,
    alter column ult_nsu set not null,
    alter column cursor_token set default '',
    alter column cursor_token set not null;

create function recusar_retrocesso_ult_nsu() returns trigger as $$
begin
    if new.ult_nsu < old.ult_nsu then
        raise exception 'retrocesso de ult_nsu não permitido em %: % -> %', tg_table_name, old.ult_nsu, new.ult_nsu;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger estado_coleta_nao_retrocede
    before update on estado_coleta
    for each row execute function recusar_retrocesso_ult_nsu();
