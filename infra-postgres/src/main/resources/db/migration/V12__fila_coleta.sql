-- Índice parcial da fila de coleta (E4-S01): claim ordena pelos vencidos há mais tempo.
create index ix_estado_coleta_fila on estado_coleta (due_at nulls first) where estado = 'ATIVO';
