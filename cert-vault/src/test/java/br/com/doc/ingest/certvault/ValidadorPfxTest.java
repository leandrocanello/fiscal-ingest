package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.certvault.ResultadoValidacao.PfxValidado;
import br.com.doc.ingest.certvault.ResultadoValidacao.Recusado;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.cofre.MotivoRecusa;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    // ===== regras: validade → cadeia → CNPJ (DA-36) =====

    private static final Instant MEIO = Instant.parse("2026-06-01T00:00:00Z");

    private ValidadorPfx validadorEm(Instant agora) {
        return new ValidadorPfx(new ConfiancaIcp(Set.of(gerador.raiz()), List.of(gerador.intermediaria())),
                new RelogioManual(agora), ValidadorPfx.LIMITE_PADRAO_BYTES);
    }

    private static ResultadoValidacao validarCom(ValidadorPfx v, byte[] pfx, String cnpjTitular) {
        return v.validar(pfx, SENHA.clone(), new Cnpj(cnpjTitular));
    }

    @Test
    @DisplayName("recusa vencido e ainda não iniciado; notBefore e notAfter exatos ainda valem")
    void recusaVencidoENaoIniciado() {
        Instant de = Instant.parse("2026-03-01T00:00:00Z");
        Instant ate = Instant.parse("2026-09-01T00:00:00Z");
        byte[] pfx = gerador.pfxModerno(gerador.titularComValidade(CNPJ, de, ate), SENHA);

        assertThat(motivo(validarCom(validadorEm(de.minusSeconds(1)), pfx, CNPJ)))
                .isEqualTo(MotivoRecusa.FORA_DA_VALIDADE);
        assertThat(motivo(validarCom(validadorEm(ate.plusSeconds(1)), pfx, CNPJ)))
                .isEqualTo(MotivoRecusa.FORA_DA_VALIDADE);
        assertThat(validarCom(validadorEm(de), pfx, CNPJ)).isInstanceOf(PfxValidado.class);
        assertThat(validarCom(validadorEm(ate), pfx, CNPJ)).isInstanceOf(PfxValidado.class);
    }

    @Test
    @DisplayName("a validade é conferida antes da cadeia e do CNPJ")
    void validadeVemAntesDaCadeiaEDoCnpj() {
        byte[] pfx = gerador.pfxModerno(gerador.autoassinado(CNPJ), SENHA);

        assertThat(motivo(validarCom(validadorEm(ATE.plus(Duration.ofDays(1))), pfx, "99888777000100")))
                .isEqualTo(MotivoRecusa.FORA_DA_VALIDADE);
    }

    @Test
    @DisplayName("recusa certificado autoassinado")
    void recusaAutoassinado() {
        byte[] pfx = gerador.pfxModerno(gerador.autoassinado(CNPJ), SENHA);

        assertThat(motivo(validarCom(validadorEm(MEIO), pfx, CNPJ))).isEqualTo(MotivoRecusa.CADEIA_NAO_CONFIAVEL);
    }

    @Test
    @DisplayName("recusa certificado de AC falsa com os mesmos nomes da verdadeira")
    void recusaAcFalsaDeMesmoNome() {
        byte[] pfx = gerador.pfxModerno(gerador.deAcFalsaDeMesmoNome(CNPJ), SENHA);

        assertThat(motivo(validarCom(validadorEm(MEIO), pfx, CNPJ))).isEqualTo(MotivoRecusa.CADEIA_NAO_CONFIAVEL);
    }

    @Test
    @DisplayName("recusa certificado emitido por um certificado final (sem cA)")
    void recusaEmitidoPorCertificadoFinal() {
        byte[] pfx = gerador.pfxModerno(gerador.emitidoPorCertificadoFinal(CNPJ), SENHA);

        assertThat(motivo(validarCom(validadorEm(MEIO), pfx, CNPJ))).isEqualTo(MotivoRecusa.CADEIA_NAO_CONFIAVEL);
    }

    @Test
    @DisplayName("recusa quando a AC já venceu na data do relógio, mesmo com o titular válido")
    void recusaAcVencidaNaDataDoRelogio() {
        Instant ateTitular = ATE.plus(Duration.ofDays(365));
        byte[] pfx = gerador.pfxModerno(gerador.titularComValidade(CNPJ, DE, ateTitular), SENHA);

        assertThat(motivo(validarCom(validadorEm(ATE.plus(Duration.ofDays(30))), pfx, CNPJ)))
                .isEqualTo(MotivoRecusa.CADEIA_NAO_CONFIAVEL);
    }

    @Test
    @DisplayName("aceita .pfx sem intermediárias se o acervo as tiver; recusa se não tiver")
    void aceitaPfxSemIntermediariasSeOAcervoTiver() {
        byte[] pfx = gerador.pfxSemIntermediarias(gerador.titular(CNPJ), SENHA);
        ValidadorPfx soRaiz = new ValidadorPfx(new ConfiancaIcp(Set.of(gerador.raiz()), List.of()),
                new RelogioManual(MEIO), ValidadorPfx.LIMITE_PADRAO_BYTES);

        assertThat(validarCom(validadorEm(MEIO), pfx, CNPJ)).isInstanceOf(PfxValidado.class);
        assertThat(motivo(validarCom(soRaiz, pfx, CNPJ))).isEqualTo(MotivoRecusa.CADEIA_NAO_CONFIAVEL);
    }

    @Test
    @DisplayName("recusa certificado sem CNPJ no otherName (sem SAN ou e-CPF)")
    void recusaSemCnpj() {
        byte[] semSan = gerador.pfxModerno(gerador.semSan(CNPJ), SENHA);
        byte[] eCpf = gerador.pfxModerno(gerador.eCpf("12345678909"), SENHA);

        assertThat(motivo(validarCom(validadorEm(MEIO), semSan, CNPJ))).isEqualTo(MotivoRecusa.SEM_CNPJ);
        assertThat(motivo(validarCom(validadorEm(MEIO), eCpf, CNPJ))).isEqualTo(MotivoRecusa.SEM_CNPJ);
    }

    @Test
    @DisplayName("recusa CNPJ divergente do titular, inclusive filial de mesma raiz (DA-36)")
    void recusaCnpjDivergente() {
        byte[] pfx = gerador.pfxModerno(gerador.titular(CNPJ), SENHA);

        assertThat(motivo(validarCom(validadorEm(MEIO), pfx, "99888777000100")))
                .isEqualTo(MotivoRecusa.CNPJ_DIVERGENTE);
        assertThat(motivo(validarCom(validadorEm(MEIO), pfx, "11222333000262")))
                .isEqualTo(MotivoRecusa.CNPJ_DIVERGENTE);
    }

    @Test
    @DisplayName("aceita certificado válido, de cadeia confiável e do titular, em qualquer codificação do CNPJ")
    void aceitaCertificadoValido() {
        for (GeradorCertificadoDeTeste.CodificacaoCnpj codificacao : GeradorCertificadoDeTeste.CodificacaoCnpj.values()) {
            byte[] pfx = gerador.pfxModerno(gerador.titular(CNPJ, codificacao), SENHA);

            assertThat(validarCom(validadorEm(MEIO), pfx, CNPJ)).isInstanceOf(PfxValidado.class);
        }
    }
}
