package br.com.doc.ingest.adapter.nfe.transporte;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Inv. 14: só WireMock em localhost. */
class TransporteHttpJdkTest {

    private static final String CAMINHO = "/ws/teste";
    private static final String ACTION = "http://exemplo.local/acao";

    private WireMockServer servidor;
    private TransporteHttpJdk transporte;

    @BeforeEach
    void subir() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
        transporte = new TransporteHttpJdk(HttpClient.newHttpClient(), Duration.ofSeconds(5), 1024 * 1024);
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + servidor.port() + CAMINHO);
    }

    @Test
    void enviaPostSoap12ComActionEBytesExatos() {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200)));
        byte[] envelope = "<a>áé</a>".getBytes(StandardCharsets.UTF_8);

        transporte.enviar(endpoint(), ACTION, envelope);

        servidor.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withHeader("Content-Type", equalTo("application/soap+xml; charset=utf-8; action=\"" + ACTION + "\""))
                .withRequestBody(binaryEqualTo(envelope)));
    }

    @Test
    void status5xxE4xxVoltamComoRespostaHttp() {
        String fault = "<Envelope><Body><Fault>erro sintetico</Fault></Body></Envelope>";
        for (int status : new int[] {500, 404}) {
            servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(status).withBody(fault)));

            ResultadoTransporte r = transporte.enviar(endpoint(), ACTION, new byte[] {1});

            assertThat(r).isInstanceOf(ResultadoTransporte.RespostaHttp.class);
            ResultadoTransporte.RespostaHttp resp = (ResultadoTransporte.RespostaHttp) r;
            assertThat(resp.status()).isEqualTo(status);
            assertThat(resp.corpo()).isEqualTo(fault.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void respostaDevolveCorpoByteAByte() {
        byte[] corpo = {0, 1, 2, (byte) 0xFF, (byte) 0xC3, 10, 13, 127};
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200).withBody(corpo)));

        ResultadoTransporte r = transporte.enviar(endpoint(), ACTION, new byte[] {1});

        assertThat(r).isInstanceOf(ResultadoTransporte.RespostaHttp.class);
        ResultadoTransporte.RespostaHttp resp = (ResultadoTransporte.RespostaHttp) r;
        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.corpo()).isEqualTo(corpo);
    }
}
