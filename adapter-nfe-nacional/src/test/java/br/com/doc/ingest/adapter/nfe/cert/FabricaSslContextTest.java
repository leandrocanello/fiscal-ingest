package br.com.doc.ingest.adapter.nfe.cert;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.adapter.Credencial;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * mTLS só contra WireMock HTTPS em localhost (inv. 14). Keystores do servidor existem só em {@code @TempDir}
 * e são gerados na hora (inv. 13).
 */
class FabricaSslContextTest {

    private static final String SENHA_SERVIDOR = "senha-servidor-sintetica";
    private static final char[] SENHA_TITULAR = "senha-titular-sintetica".toCharArray();
    private static final Instant DE = Instant.now().minus(Duration.ofDays(1));
    private static final Instant ATE = Instant.now().plus(Duration.ofDays(30));
    private static final String CAMINHO = "/ws/NFeDistribuicaoDFe";

    @TempDir
    Path pasta;

    private final CarregadorPfx carregador = new CarregadorPfx();
    private final FabricaSslContext fabrica = new FabricaSslContext();

    private WireMockServer servidor;
    private CertificadoCarregado titular;
    private KeyStore confiancaNoServidor;

    @BeforeEach
    void subir() throws Exception {
        char[] senhaServidor = SENHA_SERVIDOR.toCharArray();
        byte[] pfxServidor = GeradorPfxDeTeste.servidorLocalhost(senhaServidor, DE, ATE);
        Path keystoreServidor = pasta.resolve("servidor.p12");
        Files.write(keystoreServidor, pfxServidor);
        confiancaNoServidor = keyStoreCom(carregador.carregar(new Credencial(pfxServidor, senhaServidor)).cadeia()[0]);

        titular = carregador.carregar(new Credencial(GeradorPfxDeTeste.moderno(SENHA_TITULAR, DE, ATE), SENHA_TITULAR));
        Path truststoreServidor = pasta.resolve("confianca-servidor.p12");
        try (OutputStream saida = Files.newOutputStream(truststoreServidor)) {
            keyStoreCom(titular.cadeia()[0]).store(saida, SENHA_SERVIDOR.toCharArray());
        }

        servidor = new WireMockServer(options()
                .httpDisabled(true)
                .dynamicHttpsPort()
                .keystorePath(keystoreServidor.toString())
                .keystoreType("PKCS12")
                .keystorePassword(SENHA_SERVIDOR)
                .keyManagerPassword(SENHA_SERVIDOR)
                .needClientAuth(true)
                .trustStorePath(truststoreServidor.toString())
                .trustStoreType("PKCS12")
                .trustStorePassword(SENHA_SERVIDOR));
        servidor.start();
        servidor.stubFor(get(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    @Test
    void handshakeMtlsComCertificadoDoTitular() throws Exception {
        SSLContext contexto = fabrica.criar(titular, confiancaNoServidor);

        HttpResponse<String> resposta = chamar(contexto);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).isEqualTo("ok");
    }

    @Test
    void semCertificadoClienteOHandshakeFalha() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(confiancaNoServidor);
        SSLContext semCertificado = SSLContext.getInstance("TLS");
        semCertificado.init(null, tmf.getTrustManagers(), null);

        assertThatThrownBy(() -> chamar(semCertificado)).isInstanceOf(IOException.class);
    }

    @Test
    void servidorForaDaConfiancaOHandshakeFalha() throws Exception {
        KeyStore confiancaSoNoTitular = keyStoreCom(titular.cadeia()[0]);
        SSLContext contexto = fabrica.criar(titular, confiancaSoNoTitular);

        assertThatThrownBy(() -> chamar(contexto)).isInstanceOf(IOException.class);
    }

    @Test
    void confiancaEObrigatoria() {
        assertThatThrownBy(() -> fabrica.criar(titular, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fabrica.criar(null, confiancaNoServidor)).isInstanceOf(NullPointerException.class);
    }

    private HttpResponse<String> chamar(SSLContext contexto) throws Exception {
        HttpClient cliente = HttpClient.newBuilder()
                .sslContext(contexto)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("https://localhost:" + servidor.httpsPort() + CAMINHO))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private static KeyStore keyStoreCom(X509Certificate certificado) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("confiavel", certificado);
        return ks;
    }
}
