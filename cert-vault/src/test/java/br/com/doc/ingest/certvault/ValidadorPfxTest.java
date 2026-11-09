package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.certvault.ResultadoValidacao.PfxValidado;
import br.com.doc.ingest.certvault.ResultadoValidacao.Recusado;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.cofre.MotivoRecusa;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidadorPfx (upload de .pfx)")
class ValidadorPfxTest {

    private static final String CNPJ = "11222333000181";
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    private static final char[] SENHA = "s3nh@-sintetica".toCharArray();

    private final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(DE, ATE);
    private final ValidadorPfx validador = new ValidadorPfx(
            new ConfiancaIcp(Set.of(gerador.raiz()), List.of(gerador.intermediaria())),
            new RelogioManual(Instant.parse("2026-06-01T00:00:00Z")), ValidadorPfx.LIMITE_PADRAO_BYTES);

    private ResultadoValidacao validar(byte[] pfx) {
        return validador.validar(pfx, SENHA.clone(), new Cnpj(CNPJ));
    }

    private static MotivoRecusa motivo(ResultadoValidacao resultado) {
        assertThat(resultado).isInstanceOf(Recusado.class);
        return ((Recusado) resultado).motivo();
    }

    @Test
    @DisplayName("abre o .pfx moderno e devolve as datas do certificado do titular")
    void abrePfxModerno() {
        CertificadoTitular titular = gerador.titular(CNPJ);

        ResultadoValidacao resultado = validar(gerador.pfxModerno(titular, SENHA));

        assertThat(resultado).isInstanceOf(PfxValidado.class);
        PfxValidado validado = (PfxValidado) resultado;
        assertThat(validado.validoDe()).isEqualTo(titular.certificado().getNotBefore().toInstant());
        assertThat(validado.validoAte()).isEqualTo(titular.certificado().getNotAfter().toInstant());
    }

    @Test
    @DisplayName("abre o .pfx legado (3DES/RC2-40/MAC SHA-1)")
    void abrePfxLegado() {
        CertificadoTitular titular = gerador.titular(CNPJ);

        assertThat(validar(gerador.pfxLegado(titular, SENHA))).isInstanceOf(PfxValidado.class);
    }

    @Test
    @DisplayName("recusa acima do limite antes de tentar abrir")
    void recusaAcimaDoLimiteAntesDeAbrir() {
        ValidadorPfx pequeno = new ValidadorPfx(
                new ConfiancaIcp(Set.of(gerador.raiz()), List.of()), new RelogioManual(DE), 16);
        byte[] lixo = new byte[17];

        assertThat(motivo(pequeno.validar(lixo, SENHA.clone(), new Cnpj(CNPJ))))
                .isEqualTo(MotivoRecusa.TAMANHO_EXCEDIDO);
        assertThat(motivo(validar(new byte[ValidadorPfx.LIMITE_PADRAO_BYTES + 1])))
                .isEqualTo(MotivoRecusa.TAMANHO_EXCEDIDO);
        assertThat(ValidadorPfx.LIMITE_PADRAO_BYTES).isEqualTo(64 * 1024);
    }

    @Test
    @DisplayName("recusa bytes corrompidos como ilegíveis")
    void recusaBytesCorrompidos() {
        byte[] pfx = gerador.pfxModerno(gerador.titular(CNPJ), SENHA);
        byte[] truncado = Arrays.copyOf(pfx, pfx.length / 2);

        assertThat(motivo(validar(truncado))).isEqualTo(MotivoRecusa.ILEGIVEL);
        assertThat(motivo(validar(new byte[] {1, 2, 3}))).isEqualTo(MotivoRecusa.ILEGIVEL);
        assertThat(motivo(validar(new byte[0]))).isEqualTo(MotivoRecusa.ILEGIVEL);
    }

    @Test
    @DisplayName("recusa senha errada")
    void recusaSenhaErrada() {
        byte[] pfx = gerador.pfxModerno(gerador.titular(CNPJ), SENHA);

        assertThat(motivo(validador.validar(pfx, "outra".toCharArray(), new Cnpj(CNPJ))))
                .isEqualTo(MotivoRecusa.SENHA_INCORRETA);
    }

    @Test
    @DisplayName("recusa .pfx sem chave privada")
    void recusaSemChavePrivada() {
        byte[] pfx = gerador.pfxSemChave(gerador.titular(CNPJ), SENHA);

        assertThat(motivo(validar(pfx))).isEqualTo(MotivoRecusa.SEM_CHAVE_PRIVADA);
    }

    @Test
    @DisplayName("recusa .pfx com duas chaves privadas")
    void recusaComDuasChaves() {
        byte[] pfx = gerador.pfxComDuasChaves(CNPJ, SENHA);

        assertThat(motivo(validar(pfx))).isEqualTo(MotivoRecusa.AMBIGUO);
    }

    @Test
    @DisplayName("o fingerprint é o SHA-256 dos bytes do .pfx, e os arrays do chamador ficam intactos")
    void fingerprintEhOSha256DoPfx() {
        byte[] pfx = gerador.pfxModerno(gerador.titular(CNPJ), SENHA);
        byte[] original = pfx.clone();
        char[] senha = SENHA.clone();

        ResultadoValidacao resultado = validador.validar(pfx, senha, new Cnpj(CNPJ));

        assertThat(((PfxValidado) resultado).fingerprint()).isEqualTo(Sha256.hex(original));
        assertThat(pfx).isEqualTo(original);
        assertThat(senha).isEqualTo(SENHA);
    }

    @Test
    @DisplayName("a recusa não mostra a senha nem os bytes do .pfx")
    void recusaNaoMostraSenhaNemBytes() {
        byte[] pfx = gerador.pfxModerno(gerador.titular(CNPJ), SENHA);
        String hex = HexFormat.of().formatHex(pfx, 0, 16);

        ResultadoValidacao errada = validador.validar(pfx, "senha-errada-xyz".toCharArray(), new Cnpj(CNPJ));
        ResultadoValidacao truncado = validar(Arrays.copyOf(pfx, 40));

        for (ResultadoValidacao r : List.of(errada, truncado)) {
            assertThat(r.toString())
                    .doesNotContain("senha-errada-xyz")
                    .doesNotContain(new String(SENHA))
                    .doesNotContainIgnoringCase(hex)
                    .doesNotContain(Arrays.toString(Arrays.copyOf(pfx, 8)).replace("]", ""));
        }
    }
}
