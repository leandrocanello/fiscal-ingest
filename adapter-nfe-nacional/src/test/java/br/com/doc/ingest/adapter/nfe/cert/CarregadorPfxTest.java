package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class CarregadorPfxTest {

    private static final char[] SENHA = "senha-sintetica".toCharArray();
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");

    private final CarregadorPfx carregador = new CarregadorPfx();

    @Test
    void carregaPfxModerno() {
        byte[] pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);

        CertificadoCarregado cert = carregador.carregar(new Credencial(pfx, SENHA));

        assertThat(cert.chavePrivada().getAlgorithm()).isEqualTo("RSA");
        assertThat(cert.cadeia()).hasSize(1);
        assertThat(cert.cadeia()[0].getSubjectX500Principal().getName()).contains(GeradorPfxDeTeste.CN);
        assertThat(cert.cadeia()[0].getPublicKey().getAlgorithm()).isEqualTo("RSA");
        assertThat(cert.fingerprint()).isEqualTo(Sha256.hex(pfx));
        assertThat(cert.validoDe()).isEqualTo(DE.truncatedTo(ChronoUnit.SECONDS));
        assertThat(cert.validoAte()).isEqualTo(ATE.truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void naoAlteraACredencial() {
        byte[] pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        Credencial credencial = new Credencial(pfx, SENHA);

        carregador.carregar(credencial);

        assertThat(credencial).isEqualTo(new Credencial(pfx, SENHA));
        assertThat(credencial.senha()).containsExactly(SENHA);
        assertThat(credencial.pfx()).isEqualTo(pfx);
    }
}
