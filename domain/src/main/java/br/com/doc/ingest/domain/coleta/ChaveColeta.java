package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;

import java.util.Objects;

/**
 * Identifica um estado de coleta: {@code (fonte, cnpj_consultado, ambiente)} (CLAUDE.md §8).
 * A sequência de NSU é por CNPJ consultado e por ambiente.
 */
public record ChaveColeta(FonteId fonte, Cnpj cnpj, Ambiente ambiente) {

    public ChaveColeta {
        Objects.requireNonNull(fonte, "fonte");
        Objects.requireNonNull(cnpj, "cnpj");
        Objects.requireNonNull(ambiente, "ambiente");
    }
}
