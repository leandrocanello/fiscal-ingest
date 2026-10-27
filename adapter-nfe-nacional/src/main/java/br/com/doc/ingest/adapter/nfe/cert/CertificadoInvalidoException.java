package br.com.doc.ingest.adapter.nfe.cert;

import java.util.Objects;

/**
 * O .pfx recebido não pôde ser carregado.
 *
 * <p>A mensagem é fixa por motivo e nenhuma causa é encadeada, para nunca levar senha nem bytes do .pfx
 * para log ou exceção (inv. 13).
 */
public class CertificadoInvalidoException extends RuntimeException {

    private final MotivoCertificadoInvalido motivo;

    public CertificadoInvalidoException(MotivoCertificadoInvalido motivo) {
        super("certificado inválido: " + Objects.requireNonNull(motivo, "motivo"));
        this.motivo = motivo;
    }

    public MotivoCertificadoInvalido motivo() {
        return motivo;
    }
}
