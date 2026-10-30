package br.com.doc.ingest.domain.outbox;

/** Porta de saída do relay do outbox. Qualquer exceção significa falha de publicação (a mensagem fica pendente). */
public interface Publicador {

    void publicar(MensagemOutbox mensagem);
}
