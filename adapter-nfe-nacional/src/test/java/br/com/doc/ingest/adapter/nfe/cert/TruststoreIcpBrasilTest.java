package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TruststoreIcpBrasilTest {

    @TempDir
    Path dir;

    @Test
    void carregaCertificadosDeUmDiretorioEConfereOIndice() throws Exception {
        escreverIndice(dir, certPem("a.crt", "AC TESTE A"), certDer("b.crt", "AC TESTE B"));

        KeyStore ks = TruststoreIcpBrasil.deDiretorio(dir);

        assertThat(Collections.list(ks.aliases())).hasSize(2);
    }

    @Test
    void arquivoListadoAusenteFalhaComONomeDoArquivo() throws Exception {
        Files.writeString(dir.resolve("indice.txt"), "sumiu.crt " + "0".repeat(64) + "\n");

        assertThatThrownBy(() -> TruststoreIcpBrasil.deDiretorio(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sumiu.crt");
    }

    @Test
    void sha256DivergenteFalhaComONomeDoArquivo() throws Exception {
        certPem("a.crt", "AC TESTE A");
        Files.writeString(dir.resolve("indice.txt"), "a.crt " + "0".repeat(64) + "\n");

        assertThatThrownBy(() -> TruststoreIcpBrasil.deDiretorio(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a.crt");
    }

    @Test
    void arquivoQueNaoEUmCertificadoFalhaSemRevelarOConteudo() throws Exception {
        byte[] lixo = "SEGREDO-NAO-VAZAR".getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("x.crt"), lixo);
        Files.writeString(dir.resolve("indice.txt"), "x.crt " + sha256(lixo) + "\n");

        assertThatThrownBy(() -> TruststoreIcpBrasil.deDiretorio(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.crt")
                .hasMessageNotContaining("SEGREDO-NAO-VAZAR");
    }

    @Test
    void indiceVazioFalha() throws Exception {
        Files.writeString(dir.resolve("indice.txt"), "\n\n");

        assertThatThrownBy(() -> TruststoreIcpBrasil.deDiretorio(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indice.txt");
    }

    @Test
    void sobrescritaPorVariavelDeAmbienteTemPrioridade(@TempDir Path padrao) throws Exception {
        escreverIndice(dir, certPem("a.crt", "AC SOBRESCRITA"));
        Path recursos = Files.createDirectories(padrao.resolve("icp-brasil"));
        escreverIndice(recursos, certPemEm(recursos, "p1.crt", "AC P1"), certPemEm(recursos, "p2.crt", "AC P2"));

        KeyStore ks = TruststoreIcpBrasil.carregar(Map.of("FISCAL_TRUSTSTORE", dir.toString()), loader(padrao));

        assertThat(Collections.list(ks.aliases())).hasSize(1);
    }

    @Test
    void sobrescritaAceitaArquivoPkcs12ComSenhaVazia() throws Exception {
        KeyStore origem = KeyStore.getInstance("PKCS12");
        origem.load(null, null);
        origem.setCertificateEntry("ac", novoCertificado("AC P12"));
        Path p12 = dir.resolve("ts.p12");
        try (var out = Files.newOutputStream(p12)) {
            origem.store(out, new char[0]);
        }

        KeyStore ks = TruststoreIcpBrasil.carregar(Map.of("FISCAL_TRUSTSTORE", p12.toString()), loader(dir));

        assertThat(Collections.list(ks.aliases())).hasSize(1);
    }

    @Test
    void semVariavelUsaOPadraoDoClasspath(@TempDir Path padrao) throws Exception {
        Path recursos = Files.createDirectories(padrao.resolve("icp-brasil"));
        escreverIndice(recursos, certPemEm(recursos, "p1.crt", "AC P1"), certPemEm(recursos, "p2.crt", "AC P2"));

        KeyStore ks = TruststoreIcpBrasil.carregar(Map.of(), loader(padrao));

        assertThat(Collections.list(ks.aliases())).hasSize(2);
    }

    @Test
    void variavelApontandoParaCaminhoInexistenteFalhaComONomeDaVariavel() {
        Path inexistente = dir.resolve("nao-existe");

        assertThatThrownBy(() -> TruststoreIcpBrasil.carregar(
                Map.of("FISCAL_TRUSTSTORE", inexistente.toString()), loader(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FISCAL_TRUSTSTORE");
    }

    private static ClassLoader loader(Path raiz) throws Exception {
        return new java.net.URLClassLoader(new java.net.URL[] {raiz.toUri().toURL()}, null);
    }

    private static void escreverIndice(Path dir, String... linhas) throws Exception {
        Files.writeString(dir.resolve("indice.txt"), String.join("\n", linhas) + "\n");
    }

    private String certPem(String nome, String cn) throws Exception {
        return certPemEm(dir, nome, cn);
    }

    private String certPemEm(Path destino, String nome, String cn) throws Exception {
        byte[] der = novoCertificado(cn).getEncoded();
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END CERTIFICATE-----\n";
        byte[] bytes = pem.getBytes(StandardCharsets.US_ASCII);
        Files.write(destino.resolve(nome), bytes);
        return nome + " " + sha256(bytes);
    }

    private String certDer(String nome, String cn) throws Exception {
        byte[] der = novoCertificado(cn).getEncoded();
        Files.write(dir.resolve(nome), der);
        return nome + " " + sha256(der);
    }

    private static X509Certificate novoCertificado(String cn) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair par = g.generateKeyPair();
        X500Name nome = new X500Name("CN=" + cn);
        Instant agora = Instant.now();
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(nome,
                new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE),
                Date.from(agora.minus(1, ChronoUnit.DAYS)), Date.from(agora.plus(365, ChronoUnit.DAYS)),
                nome, par.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        return new JcaX509CertificateConverter().getCertificate(
                b.build(new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate())));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
