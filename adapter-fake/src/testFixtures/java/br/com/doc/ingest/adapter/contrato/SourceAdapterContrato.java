package br.com.doc.ingest.adapter.contrato;

import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.SourceAdapter;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.model.Documento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suíte de contrato: o que o core pode esperar de QUALQUER {@link SourceAdapter}.
 *
 * <p>Só usa tipos do domain (inv. 4). A subclasse monta o adapter e o cenário nos ganchos abaixo
 * (o {@code @BeforeEach} de montagem fica na subclasse).
 */
public abstract class SourceAdapterContrato {

    protected abstract SourceAdapter adapter();

    protected abstract ContextoConsulta contexto();

    protected abstract BlobStore blobs();

    /** Prepara a fonte para devolver {@code n} documentos (NSU 1..n) a partir do cursor inicial. */
    protected abstract void prepararLote(int n);

    /** Prepara a fonte para não ter nada novo. */
    protected abstract void prepararVazio();

    /** Prepara a fonte para falhar de forma temporária. */
    protected abstract void prepararFalhaTemporaria();

    @Test
    @DisplayName("descritor é da fonte do contexto e declara capacidades")
    void descritorDaFonteDoContexto() {
        assertThat(adapter().descritor().fonteId()).isEqualTo(contexto().fonte());
        assertThat(adapter().descritor().capacidades()).isNotNull();
    }

    @Test
    @DisplayName("lote Ok traz documentos coerentes (fonte, NSUs distintos <= ultNsu, ids distintos)")
    void loteOkTemDocumentosCoerentes() {
        prepararLote(3);

        BuscarLoteResultado r = adapter().buscarLote(contexto(), Cursor.inicial());

        assertThat(r.desfecho()).isInstanceOf(Desfecho.Ok.class);
        List<Documento> docs = r.documentos();
        assertThat(docs).hasSize(3);
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.fonte()).isEqualTo(contexto().fonte());
            assertThat(d.nsu().valor()).isLessThanOrEqualTo(r.ultNsu().valor());
        });
        assertThat(docs.stream().map(Documento::nsu).distinct()).hasSize(3);
        assertThat(docs.stream().map(Documento::id).distinct()).hasSize(3);
    }
}
