package br.com.doc.ingest.adapter.nfe.cert;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Objects;

/**
 * Certificado A1 aberto em memória: chave privada, cadeia e metadados.
 *
 * <p>Vive só no adapter (nunca no domínio, inv. 3/4). {@link #toString()} mostra apenas fingerprint e
 * validade, nunca a chave, a cadeia ou o titular (inv. 13).
 *
 * @param chavePrivada chave privada do titular
 * @param cadeia       cadeia de certificados, começando pelo do titular
 * @param fingerprint  SHA-256 em hexadecimal dos bytes do .pfx
 * @param validoDe     início da validade do certificado do titular
 * @param validoAte    fim da validade do certificado do titular
 */
public record CertificadoCarregado(PrivateKey chavePrivada, X509Certificate[] cadeia, String fingerprint,
                                   Instant validoDe, Instant validoAte) {

    public CertificadoCarregado {
        Objects.requireNonNull(chavePrivada, "chavePrivada");
        Objects.requireNonNull(cadeia, "cadeia");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(validoDe, "validoDe");
        Objects.requireNonNull(validoAte, "validoAte");
        if (cadeia.length == 0) {
            throw new IllegalArgumentException("cadeia não pode ser vazia");
        }
        cadeia = cadeia.clone();
    }

    /** @return cópia da cadeia */
    @Override
    public X509Certificate[] cadeia() {
        return cadeia.clone();
    }

    @Override
    public String toString() {
        return "CertificadoCarregado[fingerprint=" + fingerprint + ", validoDe=" + validoDe
                + ", validoAte=" + validoAte + "]";
    }
}
