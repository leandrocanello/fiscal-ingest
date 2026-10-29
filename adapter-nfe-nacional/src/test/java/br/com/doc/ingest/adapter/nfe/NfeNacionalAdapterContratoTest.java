package br.com.doc.ingest.adapter.nfe;

import static br.com.doc.ingest.adapter.nfe.RespostasDistDfeDeTeste.NFE_NS;
import static br.com.doc.ingest.adapter.nfe.RespostasDistDfeDeTeste.docZip;
import static br.com.doc.ingest.adapter.nfe.RespostasDistDfeDeTeste.resposta;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import br.com.doc.ingest.adapter.contrato.SourceAdapterContrato;
import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteHttpJdk;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.SourceAdapter;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Roda a suíte de contrato do SourceAdapter contra o NfeNacionalAdapter com WireMock em localhost (inv. 14). */
class NfeNacionalAdapterContratoTest extends SourceAdapterContrato {

    private static final String CAMINHO = "/ws/distdfe";
    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");

    private WireMockServer servidor;
    private BlobStoreEmMemoria blobs;
    private NfeNacionalAdapter adapter;

    @BeforeEach
    void montar() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
        blobs = new BlobStoreEmMemoria();
        URI endpoint = URI.create("http://localhost:" + servidor.port() + CAMINHO);
        adapter = new NfeNacionalAdapter(FONTE, TipoAmbiente.PRODUCAO, endpoint, blobs,
                Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC),
                credencial -> new TransporteHttpJdk(HttpClient.newHttpClient(), Duration.ofSeconds(5), 1024 * 1024));
        adapter.autenticar(new Credencial(new byte[] {1}, new char[0]));
    }

    @AfterEach
    void parar() {
        servidor.stop();
    }

    @Override
    protected SourceAdapter adapter() {
        return adapter;
    }

    @Override
    protected ContextoConsulta contexto() {
        return new ContextoConsulta(FONTE, CNPJ);
    }

    @Override
    protected BlobStore blobs() {
        return blobs;
    }

    @Override
    protected Optional<byte[]> lerBlob(Documento documento) {
        String chave = documento.blobRef().orElseThrow();
        String sha = chave.substring(chave.lastIndexOf('-') + 1, chave.length() - ".xml".length());
        return blobs.ler(new BlobRef(chave, sha));
    }

    @Override
    protected void prepararLote(int n) {
        StringBuilder docs = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            docs.append(docZip(i, "procNFe_v4.00.xsd",
                    "<nfeProc xmlns=\"" + NFE_NS + "\"><NFe>sintetica " + i + "</NFe></nfeProc>"));
        }
        // 1ª consulta (ultNSU 0) traz o lote; as seguintes, nada novo
        servidor.stubFor(post(urlEqualTo(CAMINHO)).atPriority(2)
                .willReturn(soap(resposta("137", n, n, ""))));
        servidor.stubFor(post(urlEqualTo(CAMINHO)).atPriority(1)
                .withRequestBody(containing("<ultNSU>000000000000000</ultNSU>"))
                .willReturn(soap(resposta("138", n, n, docs.toString()))));
    }

    @Override
    protected void prepararVazio() {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(soap(resposta("137", 0, 0, ""))));
    }

    @Override
    protected void prepararFalhaTemporaria() {
        servidor.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(503)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder soap(String corpo) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/soap+xml; charset=utf-8").withBody(corpo);
    }
}
