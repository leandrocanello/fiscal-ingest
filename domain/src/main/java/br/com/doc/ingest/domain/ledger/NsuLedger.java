package br.com.doc.ingest.domain.ledger;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;

import java.util.List;

/**
 * Porta do NSU Ledger - o core so define a interface; a impl fica na infra.
 */
public interface NsuLedger {

    /**
     * Registra uma entrada no ledger (append-only, idempotente).
     */
    void registrar(FonteId fonte, Cnpj cnpj, EntradaLedger entrada);

    /**
     * Lista todas as entradas do ledger para um (fonte, cnpj).
     * Usado pelo GapCalculator para identificar buracos na sequencia.
     */
    List<EntradaLedger> listarEntradas(FonteId fonte, Cnpj cnpj);
}
