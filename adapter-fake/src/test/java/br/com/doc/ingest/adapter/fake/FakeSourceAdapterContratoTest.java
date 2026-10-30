package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.adapter.contrato.SourceAdapterContrato;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.SourceAdapter;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.BeforeEach;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Roda a suíte de contrato do SourceAdapter contra o {@link FakeSourceAdapter}. */
class FakeSourceAdapterContratoTest extends SourceAdapterContrato {

    private final FonteId fonte = new FonteId("FAKE");
    private final Cnpj cnpj = new Cnpj("11111111000191");
    private FakeSourceAdapter adapter;
    private BlobStoreEmMemoria blobs;

    @BeforeEach
    void montar() {
        adapter = new FakeSourceAdapter(fonte);
        blobs = new BlobStoreEmMemoria();
    }

    @Override
    protected SourceAdapter adapter() {
        return adapter;
    }

    @Override
    protected ContextoConsulta contexto() {
        return new ContextoConsulta(fonte, cnpj);
    }

    @Override
    protected BlobStore blobs() {
        return blobs;
    }

    @Override
    protected Optional<byte[]> lerBlob(Documento documento) {
        return blobs.ler(documento.blobRef().orElseThrow());
    }

    @Override
    protected void prepararLote(int n) {
        List<Documento> docs = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            byte[] bytes = ("<doc>sintetico " + i + "</doc>").getBytes(StandardCharsets.UTF_8);
            BlobRef ref = blobs.gravar("fake/" + i + ".xml", bytes, Sha256.hex(bytes));
            docs.add(new Documento(UUID.randomUUID(), fonte, cnpj, "55", new Nsu(i), Instant.EPOCH,
                    "procNFe", Optional.empty(), Optional.of(ref)));
        }
        adapter.preCarregar(cnpj, docs);
    }

    @Override
    protected void prepararVazio() {
        adapter.preCarregar(cnpj, List.of());
    }

    @Override
    protected void prepararFalhaTemporaria() {
        adapter.programarDesfecho(cnpj, new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5)));
    }
}
