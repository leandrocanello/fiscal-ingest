package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CodificacaoCnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GeradorCertificadoDeTeste (cadeia ICP sintética, só em memória)")
class GeradorCertificadoDeTesteTest {

    private static final String CNPJ = "11222333000181";
    private static final char[] SENHA = "senha-de-teste".toCharArray();
    private static final Instant INICIO = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FIM = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant MEIO = Instant.parse("2026-06-01T00:00:00Z");

    private final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(INICIO, FIM);

    private PKIXCertPathBuilderResult construirCaminho(X509Certificate certificado, List<X509Certificate> extras,
                                                       Instant data) throws Exception {
        return construirCaminho(gerador.raiz(), certificado, extras, data);
    }

    private static PKIXCertPathBuilderResult construirCaminho(X509Certificate raiz, X509Certificate certificado,
                                                              List<X509Certificate> extras, Instant data)
            throws Exception {
        X509CertSelector alvo = new X509CertSelector();
        alvo.setCertificate(certificado);
        PKIXBuilderParameters parametros = new PKIXBuilderParameters(
                Set.of(new TrustAnchor(raiz, null)), alvo);
        parametros.setRevocationEnabled(false);
        parametros.setDate(Date.from(data));
        List<X509Certificate> acervo = new ArrayList<>(extras);
        acervo.add(certificado);
        parametros.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(acervo)));
        return (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parametros);
    }

    private static List<byte[]> otherNames(X509Certificate certificado) throws Exception {
        List<byte[]> nomes = new ArrayList<>();
        var sans = certificado.getSubjectAlternativeNames();
        if (sans != null) {
            for (List<?> san : sans) {
                if (Integer.valueOf(0).equals(san.get(0))) {
                    nomes.add((byte[]) san.get(1));
                }
            }
        }
        return nomes;
    }

    /** {@code OtherName ::= SEQUENCE { OID 2.16.76.1.3.3, [0] EXPLICIT valor }}, conforme o JDK devolve. */
    private static String otherNameCnpjEsperado(String tagDoValor, String digitos) {
        return "3019" + "0605604c010303" + "a010" + tagDoValor + "0e"
                + HexFormat.of().formatHex(digitos.getBytes(StandardCharsets.US_ASCII));
    }

    private static List<String> otherNamesEmHex(X509Certificate certificado) throws Exception {
        return otherNames(certificado).stream().map(HexFormat.of()::formatHex).toList();
    }

    private static KeyStore abrir(byte[] pfx) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(pfx), SENHA);
        return ks;
    }

    private static int contarChaves(KeyStore ks) throws Exception {
        int chaves = 0;
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                chaves++;
            }
        }
        return chaves;
    }

    private static String aliasDaChave(KeyStore ks) throws Exception {
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new AssertionError("pfx sem chave");
    }

    @Test
    @DisplayName("raiz → intermediária → titular passa no PKIX do JDK, só com a raiz como âncora")
    void geraCadeiaRaizIntermediariaTitularQueOPkixAceita() throws Exception {
        CertificadoTitular titular = gerador.titular(CNPJ);

        PKIXCertPathBuilderResult resultado = construirCaminho(titular.certificado(),
                List.of(gerador.intermediaria()), MEIO);

        assertThat(List.copyOf(resultado.getCertPath().getCertificates()))
                .isEqualTo(List.of(titular.certificado(), gerador.intermediaria()));
        assertThat(resultado.getTrustAnchor().getTrustedCert()).isEqualTo(gerador.raiz());
        assertThat(titular.certificado().getBasicConstraints()).isEqualTo(-1);
        assertThat(gerador.intermediaria().getBasicConstraints()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("o titular traz o CNPJ no otherName 2.16.76.1.3.3, ao lado de outros otherNames")
    void titularTrazOCnpjNoOtherName() throws Exception {
        X509Certificate certificado = gerador.titular(CNPJ).certificado();

        assertThat(otherNamesEmHex(certificado))
                .hasSizeGreaterThan(1)
                .contains(otherNameCnpjEsperado("04", CNPJ));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"OCTET_STRING,04", "PRINTABLE_STRING,13", "UTF8_STRING,0c"})
    @DisplayName("o CNPJ pode vir em OCTET STRING, PrintableString ou UTF8String")
    void variantesDeCodificacaoDoCnpj(CodificacaoCnpj codificacao, String tagDoValor) throws Exception {
        X509Certificate certificado = gerador.titular(CNPJ, codificacao).certificado();

        assertThat(otherNamesEmHex(certificado)).contains(otherNameCnpjEsperado(tagDoValor, CNPJ));
    }

    @Test
    @DisplayName("variantes sem CNPJ: sem SAN traz o CNPJ só no CN; e-CPF usa o OID 2.16.76.1.3.1")
    void variantesSemCnpjNoOid() throws Exception {
        X509Certificate semSan = gerador.semSan(CNPJ).certificado();
        X509Certificate eCpf = gerador.eCpf("11144477735").certificado();

        assertThat(semSan.getSubjectAlternativeNames()).isNull();
        assertThat(semSan.getSubjectX500Principal().getName()).contains(CNPJ);
        assertThat(otherNamesEmHex(eCpf))
                .noneMatch(nome -> nome.contains("604c010303"))
                .anyMatch(nome -> nome.contains("604c010301"));
    }

    @Test
    @DisplayName("autoassinado, emitido por certificado final e AC falsa de mesmo nome não passam no PKIX")
    void variantesDeCadeiaInvalidaOPkixRecusa() {
        CertificadoTitular autoassinado = gerador.autoassinado(CNPJ);
        CertificadoTitular porFinal = gerador.emitidoPorCertificadoFinal(CNPJ);
        CertificadoTitular acFalsa = gerador.deAcFalsaDeMesmoNome(CNPJ);

        for (CertificadoTitular invalido : List.of(autoassinado, porFinal, acFalsa)) {
            assertThatThrownBy(() -> construirCaminho(invalido.certificado(), invalido.cadeia(), MEIO))
                    .isInstanceOf(CertPathBuilderException.class);
        }
        assertThat(acFalsa.cadeia().get(0).getSubjectX500Principal())
                .isEqualTo(gerador.intermediaria().getSubjectX500Principal());
        assertThat(acFalsa.cadeia().get(0)).isNotEqualTo(gerador.intermediaria());
    }

    @Test
    @DisplayName("a validade do titular pode passar da validade da AC, e a AC vencida barra o caminho")
    void acVencidaNaDataBarraOCaminho() throws Exception {
        GeradorCertificadoDeTeste comAcCurta = new GeradorCertificadoDeTeste(INICIO, INICIO.plus(Duration.ofDays(30)));
        CertificadoTitular titular = comAcCurta.titularComValidade(CNPJ, INICIO, FIM);

        assertThat(titular.certificado().getNotAfter()).isEqualTo(Date.from(FIM));
        assertThat(construirCaminho(comAcCurta.raiz(), titular.certificado(), titular.cadeia(),
                INICIO.plus(Duration.ofDays(1))).getCertPath().getCertificates()).hasSize(2);
        assertThatThrownBy(() -> construirCaminho(comAcCurta.raiz(), titular.certificado(), titular.cadeia(), MEIO))
                .isInstanceOf(CertPathBuilderException.class);
    }

    @Test
    @DisplayName("os .pfx moderno e legado abrem no KeyStore do JDK com chave e cadeia completa")
    void pfxModernoELegadoAbremNoKeyStoreDoJdk() throws Exception {
        CertificadoTitular titular = gerador.titular(CNPJ);

        for (byte[] pfx : List.of(gerador.pfxModerno(titular, SENHA), gerador.pfxLegado(titular, SENHA))) {
            KeyStore ks = abrir(pfx);
            String alias = aliasDaChave(ks);

            assertThat(contarChaves(ks)).isEqualTo(1);
            assertThat(ks.getKey(alias, SENHA)).isEqualTo(titular.par().getPrivate());
            assertThat(ks.getCertificateChain(alias))
                    .containsExactly(titular.certificado(), gerador.intermediaria(), gerador.raiz());
        }
    }

    @Test
    @DisplayName("variantes de .pfx: sem intermediárias, sem chave e com duas chaves")
    void variantesDePfx() throws Exception {
        CertificadoTitular titular = gerador.titular(CNPJ);

        KeyStore semIntermediarias = abrir(gerador.pfxSemIntermediarias(titular, SENHA));
        KeyStore semChave = abrir(gerador.pfxSemChave(titular, SENHA));
        KeyStore comDuasChaves = abrir(gerador.pfxComDuasChaves(CNPJ, SENHA));

        assertThat(semIntermediarias.getCertificateChain(aliasDaChave(semIntermediarias)))
                .containsExactly(titular.certificado());
        assertThat(contarChaves(semChave)).isZero();
        assertThat(semChave.size()).isEqualTo(1);
        assertThat(contarChaves(comDuasChaves)).isEqualTo(2);
    }

    @Test
    @DisplayName("o relógio manual só anda quando mandado")
    void relogioManualSoAndaQuandoMandado() {
        RelogioManual relogio = new RelogioManual(INICIO);

        assertThat(relogio.instant()).isEqualTo(INICIO);
        relogio.avancar(Duration.ofHours(1));
        assertThat(relogio.instant()).isEqualTo(INICIO.plus(Duration.ofHours(1)));
    }
}
