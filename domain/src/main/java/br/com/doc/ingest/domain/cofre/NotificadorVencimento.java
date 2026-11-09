package br.com.doc.ingest.domain.cofre;

/** Porta do canal de aviso ao tenant. Lança exceção se não conseguiu entregar. */
public interface NotificadorVencimento {

    void notificar(AlertaVencimento alerta);
}
