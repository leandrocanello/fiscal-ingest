package br.com.doc.ingest.adapter.nfe;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Smoke: WireMock 3.x (jakarta/Jetty) roda sem CXF e o HttpClient do JDK basta (inv. 14: só localhost). */
class WireMockHttpClientSmokeTest {

    private static final String CAMINHO = "/ws/NFeDistribuicaoDFe";

    private WireMockServer servidor;

    @BeforeEach
    void subir() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    @Test
    void httpClientDoJdkFalaSoap12ComWireMockLocal() throws Exception {
        servidor.stubFor(post(urlEqualTo(CAMINHO))
                .withHeader("Content-Type", containing("application/soap+xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/soap+xml; charset=utf-8")
                        .withBody("<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                                + "<soap12:Body><retDistDFeInt><cStat>137</cStat></retDistDFeInt></soap12:Body>"
                                + "</soap12:Envelope>")));

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + servidor.port() + CAMINHO))
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString("<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"/>"))
                .build();

        HttpResponse<String> resposta = HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.ofString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("retDistDFeInt");
        servidor.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withHeader("Content-Type", containing("application/soap+xml")));
    }
}
