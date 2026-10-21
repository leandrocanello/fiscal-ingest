package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;

/**
 * Contexto de uma consulta ao adapter.
 * Carrega a fonte + cnpj destinatario - o minimo para o adapter saber de quem buscar.
 */
public record ContextoConsulta(
    FonteId fonte,
    Cnpj cnpj
) {}
