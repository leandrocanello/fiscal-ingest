package br.com.doc.ingest.domain.ledger;

import br.com.doc.ingest.domain.model.Nsu;

/**
 * Registro imutável de uma entrada no NSU Ledger append-only.
 *
 * <p>Cada entrada representa um NSU processado (ou identificado como gap)
 * para um (fonte, cnpj). O {@link GapCalculator} usa o conjunto de entradas
 * para reconstruir a sequência e identificar buracos.
 *
 * @param nsu    o número sequencial único
 * @param status o desfecho do processamento deste NSU
 */
public record EntradaLedger(Nsu nsu, StatusNsu status) {
}
