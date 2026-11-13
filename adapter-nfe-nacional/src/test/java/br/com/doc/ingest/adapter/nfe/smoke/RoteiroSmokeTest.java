package br.com.doc.ingest.adapter.nfe.smoke;

import static br.com.doc.ingest.adapter.nfe.RespostasDistDfeDeTeste.NFE_NS;
import static br.com.doc.ingest.adapter.nfe.RespostasDistDfeDeTeste.docZip;
import static br.com.doc.ingest.adapter.nfe.RespostasDistDfeDeTeste.resposta;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.NfeNacionalAdapter;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteHttpJdk;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Peças do harness de smoke contra WireMock HTTP simples (inv. 14): nenhuma SEFAZ é tocada. */
@DisplayName("RoteiroSmoke")
class RoteiroSmokeTest {

    private static final String CAMINHO = "/ws/distdfe";
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");
    private static final String XML_PROC = "<nfeProc xmlns=\"" + NFE_NS + "\"><NFe>sintetica</NFe></nfeProc>";

    @TempDir
    Path dir;

    private WireMockServer servidor;
    private CursorLocal cursor;
    private ByteArrayOutputStream bytesSaida;
    private RoteiroSmoke roteiro;

    @BeforeEach
    void subir() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
        cursor = new CursorLocal(dir, CNPJ);
        bytesSaida = new ByteArrayOutputStream();
        roteiro = novoRoteiro(5);
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    private RoteiroSmoke novoRoteiro(int limitePaginas) {
        URI endpoint = URI.create("http://localhost:" + servidor.port() + CAMINHO);
        NfeNacionalAdapter adapter = new NfeNacionalAdapter(FONTE, TipoAmbiente.HOMOLOGACAO, endpoint,
                new BlobStoreEmMemoria(), Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC),
                credencial -> new TransporteHttpJdk(HttpClient.newHttpClient(), Duration.ofSeconds(5), 1024 * 1024));
        adapter.autenticar(new Credencial(new byte[] {1}, new char[0]));
        return new RoteiroSmoke(adapter, cursor, new ContextoConsulta(FONTE, CNPJ),
                new PrintStream(bytesSaida, true, StandardCharsets.UTF_8), limitePaginas);
    }

    private String saida() {
        return bytesSaida.toString(StandardCharsets.UTF_8);
    }

    private void passo(String estadoAtual, String proximo, String corpo) {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).inScenario("smoke").whenScenarioStateIs(estadoAtual)
                .willSetStateTo(proximo)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/soap+xml; charset=utf-8").withBody(corpo)));
    }

    @Test
    @DisplayName("138 com mais páginas e depois 137: grava o ultNSU do 138 e para")
    void percorreAteOVazioEGravaOCursor() {
        passo(Scenario.STARTED, "p2", resposta("138", 1, 2, docZip(1, "procNFe_v4.00", XML_PROC)));
        passo("p2", "fim", resposta("137", 1, 1, ""));

        roteiro.distNsu();

        assertThat(servidor.getAllServeEvents()).hasSize(2);
        assertThat(cursor.ler()).isEqualTo(new Nsu(1));
        assertThat(Files.exists(dir.resolve("cursor-11111111000191.txt"))).isTrue();
        assertThat(saida()).contains("Ok").contains("Vazio").contains("pare por ~1h");
    }

    @Test
    @DisplayName("parte do cursor salvo")
    void partiDoCursorSalvo() {
        cursor.gravar(new Nsu(7));
        passo(Scenario.STARTED, "fim", resposta("137", 7, 7, ""));

        roteiro.distNsu();

        assertThat(servidor.getAllServeEvents()).hasSize(1);
        assertThat(servidor.getAllServeEvents().get(0).getRequest().getBodyAsString()).contains("000000000000007");
        assertThat(cursor.ler()).isEqualTo(new Nsu(7));
    }

    @Test
    @DisplayName("ultNSU menor que o salvo aborta sem gravar (inv. 1)")
    void respostaComUltNsuMenorQueOSalvoAbortaSemGravar() {
        cursor.gravar(new Nsu(50));
        passo(Scenario.STARTED, "fim", resposta("138", 10, 20, docZip(10, "procNFe_v4.00", XML_PROC)));

        roteiro.distNsu();

        assertThat(cursor.ler()).isEqualTo(new Nsu(50));
        assertThat(servidor.getAllServeEvents()).hasSize(1);
        assertThat(saida()).contains("IndisponivelDefinitivo");
    }

    @Test
    @DisplayName("respeita o limite de páginas")
    void paraNoLimiteDePaginas() {
        roteiro = novoRoteiro(1);
        passo(Scenario.STARTED, "p2", resposta("138", 1, 9, docZip(1, "procNFe_v4.00", XML_PROC)));
        passo("p2", "p3", resposta("138", 2, 9, docZip(2, "procNFe_v4.00", XML_PROC)));

        roteiro.distNsu();

        assertThat(servidor.getAllServeEvents()).hasSize(1);
        assertThat(cursor.ler()).isEqualTo(new Nsu(1));
        assertThat(saida()).contains("limite de páginas");
    }

    @Test
    @DisplayName("saída tem Desfecho, NSU e BlobRef; sem XML nem CNPJ completo (inv. 3 e 13)")
    void saidaNaoVazaXmlNemCnpj() {
        passo(Scenario.STARTED, "fim", resposta("138", 1, 1, docZip(1, "procNFe_v4.00", XML_PROC)));

        roteiro.distNsu();

        assertThat(saida()).contains("Ok").contains("ultNSU=1").contains("raw/pull/");
        assertThat(saida()).doesNotContain("<").doesNotContain(CNPJ.valor()).contains(CNPJ.mascarado());
    }

    @Test
    @DisplayName("manifestarCiencia sem a flag imprime 'pulado' e não chama a rede")
    void manifestarSemFlagPula() {
        roteiro.manifestarCiencia("x".repeat(44), false);

        assertThat(saida()).contains("pulado");
        assertThat(servidor.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("consultarChave rejeita chave inválida sem ecoá-la")
    void consultarChaveInvalida() {
        assertThatThrownBy(() -> roteiro.consultarChave("123")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("123");
    }
}
