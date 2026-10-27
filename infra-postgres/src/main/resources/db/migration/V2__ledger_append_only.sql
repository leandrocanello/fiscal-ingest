-- Invariante 6: ledger append-only garantido no banco (UPDATE/DELETE recusados).
-- TRUNCATE fica fora do trigger (usado só em teste).

create function recusar_alteracao_ledger() returns trigger as $$
begin
    raise exception 'tabela % é append-only: % não permitido', tg_table_name, tg_op;
end;
$$ language plpgsql;

create trigger ledger_nsu_append_only
    before update or delete on ledger_nsu
    for each row execute function recusar_alteracao_ledger();

create trigger ledger_tentativa_append_only
    before update or delete on ledger_tentativa
    for each row execute function recusar_alteracao_ledger();
