package br.com.doc.ingest.adapter.nfe.evento;

/**
 * O evento não pôde ser assinado (chave incompatível, {@code Id} ausente, falha da API XML-DSig).
 *
 * <p>A mensagem é fixa e nenhuma causa é encadeada, para nunca levar chave privada, titular ou senha para
 * log ou exceção (inv. 13). Mesmo padrão de {@code CertificadoInvalidoException}.
 */
public class AssinaturaInvalidaException extends RuntimeException {

    public AssinaturaInvalidaException(String mensagem) {
        super("assinatura inválida: " + mensagem);
    }
}
