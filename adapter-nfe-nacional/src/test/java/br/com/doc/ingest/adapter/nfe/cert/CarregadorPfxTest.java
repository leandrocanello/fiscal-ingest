package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
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
    void carregaPfxLegadoRc2E3Des() {
        byte[] pfx = GeradorPfxDeTeste.legado(SENHA, DE, ATE);

        CertificadoCarregado cert = carregador.carregar(new Credencial(pfx, SENHA));

        assertThat(cert.chavePrivada().getAlgorithm()).isEqualTo("RSA");
        assertThat(cert.cadeia()).hasSize(1);
        assertThat(cert.cadeia()[0].getSubjectX500Principal().getName()).contains(GeradorPfxDeTeste.CN);
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

    @Test
    void senhaErradaViraSenhaIncorreta() {
        byte[] pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);

        assertThat(motivoAoCarregar(pfx, "outra-senha".toCharArray()))
                .isEqualTo(MotivoCertificadoInvalido.SENHA_INCORRETA);
    }

    @Test
    void bytesCorrompidosViraIlegivel() {
        byte[] pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        byte[] corrompido = java.util.Arrays.copyOf(pfx, pfx.length / 2);

        assertThat(motivoAoCarregar(corrompido, SENHA)).isEqualTo(MotivoCertificadoInvalido.ILEGIVEL);
        assertThat(motivoAoCarregar("nao e um pfx".getBytes(), SENHA)).isEqualTo(MotivoCertificadoInvalido.ILEGIVEL);
    }

    @Test
    void semChavePrivadaViraSemChave() {
        byte[] pfx = GeradorPfxDeTeste.semChavePrivada(SENHA, DE, ATE);

        assertThat(motivoAoCarregar(pfx, SENHA)).isEqualTo(MotivoCertificadoInvalido.SEM_CHAVE_PRIVADA);
    }

    @Test
    void maisDeUmaChavePrivadaViraAmbiguo() {
        byte[] pfx = GeradorPfxDeTeste.comDuasChavesPrivadas(SENHA, DE, ATE);

        assertThat(motivoAoCarregar(pfx, SENHA)).isEqualTo(MotivoCertificadoInvalido.AMBIGUO);
    }

    @Test
    void senhaErradaNaoApareceNaMensagem() {
        byte[] pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);
        String senhaErrada = "senha-errada-secreta-123";
        byte[] corrompido = java.util.Arrays.copyOf(pfx, pfx.length / 2);

        for (byte[] bytes : new byte[][] {pfx, corrompido}) {
            CertificadoInvalidoException erro = catchThrowableOfType(CertificadoInvalidoException.class,
                    () -> carregador.carregar(new Credencial(bytes, senhaErrada.toCharArray())));
            assertThat(erro).isNotNull();
            String hexPfx = HexFormat.of().formatHex(bytes, 0, 16);
            for (Throwable t = erro; t != null; t = t.getCause()) {
                assertThat(String.valueOf(t.getMessage()))
                        .doesNotContain(senhaErrada)
                        .doesNotContain(String.valueOf(SENHA))
                        .doesNotContainIgnoringCase(hexPfx);
                assertThat(String.valueOf(t)).doesNotContain(senhaErrada);
            }
        }
    }

    private MotivoCertificadoInvalido motivoAoCarregar(byte[] pfx, char[] senha) {
        CertificadoInvalidoException erro = catchThrowableOfType(CertificadoInvalidoException.class,
                () -> carregador.carregar(new Credencial(pfx, senha)));
        assertThat(erro).as("deveria lançar CertificadoInvalidoException").isNotNull();
        return erro.motivo();
    }
}
