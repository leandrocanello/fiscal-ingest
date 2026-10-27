package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.adapter.Credencial;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CertificadoCarregadoTest {

    @Test
    void toStringSoMostraFingerprintEValidade() {
        char[] senha = "senha-sintetica".toCharArray();
        CertificadoCarregado cert = new CarregadorPfx().carregar(new Credencial(GeradorPfxDeTeste.moderno(senha,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")), senha));

        String texto = cert.toString();

        assertThat(texto).contains(cert.fingerprint())
                .contains("2026-01-01T00:00:00Z")
                .contains("2027-01-01T00:00:00Z")
                .doesNotContain("senha-sintetica")
                .doesNotContain(GeradorPfxDeTeste.CN)
                .doesNotContain("RSA")
                .doesNotContain("BEGIN")
                .doesNotContain(cert.chavePrivada().toString());
    }
}
