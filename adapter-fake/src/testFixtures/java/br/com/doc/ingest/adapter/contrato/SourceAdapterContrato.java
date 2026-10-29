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
import java.util.Optional;

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

    /** Lê os bytes do blob referenciado pelo documento (a forma do {@code blobRef} varia por adapter). */
    protected abstract Optional<byte[]> lerBlob(Documento documento);

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

    @Test
    @DisplayName("todo documento tem blob legível e não vazio (inv. 3 e 5)")
    void todoDocumentoTemBlobLegivel() {
        prepararLote(3);

        BuscarLoteResultado r = adapter().buscarLote(contexto(), Cursor.inicial());

        assertThat(r.documentos()).isNotEmpty().allSatisfy(d -> {
            assertThat(d.blobRef()).isPresent();
            assertThat(lerBlob(d)).isPresent().get().asInstanceOf(
                    org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY).isNotEmpty();
        });
    }

    @Test
    @DisplayName("retomar pelo proximoCursor não reentrega nem retrocede (inv. 1)")
    void retomadaComProximoCursorNaoReentregaNemRetrocede() {
        prepararLote(3);
        BuscarLoteResultado primeiro = adapter().buscarLote(contexto(), Cursor.inicial());

        BuscarLoteResultado segundo = adapter().buscarLote(contexto(), primeiro.proximoCursor());

        assertThat(segundo.documentos()).allSatisfy(d ->
                assertThat(d.nsu().valor()).isGreaterThan(primeiro.ultNsu().valor()));
        assertThat(segundo.ultNsu().valor()).isGreaterThanOrEqualTo(primeiro.ultNsu().valor());
    }

    @Test
    @DisplayName("Vazio: sem documentos, sem mais, ultNsu não retrocede")
    void vazioNaoRetrocede() {
        prepararVazio();

        BuscarLoteResultado r = adapter().buscarLote(contexto(), Cursor.inicial());

        assertThat(r.desfecho()).isInstanceOf(Desfecho.Vazio.class);
        assertThat(r.documentos()).isEmpty();
        assertThat(r.temMais()).isFalse();
        assertThat(r.ultNsu().valor()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("falha temporária não avança o cursor")
    void falhaNaoAvancaCursor() {
        prepararFalhaTemporaria();
        Cursor entrada = Cursor.inicial();

        BuscarLoteResultado r = adapter().buscarLote(contexto(), entrada);

        assertThat(r.desfecho()).isInstanceOf(Desfecho.IndisponivelTemporario.class);
        assertThat(r.documentos()).isEmpty();
        assertThat(r.proximoCursor()).isEqualTo(entrada);
        assertThat(r.temMais()).isFalse();
    }

    @Test
    @DisplayName("desfecho, documentos e cursor nunca são nulos")
    void desfechoEDocumentosNuncaNulos() {
        prepararLote(2);

        BuscarLoteResultado r = adapter().buscarLote(contexto(), Cursor.inicial());

        assertThat(r.desfecho()).isNotNull();
        assertThat(r.documentos()).isNotNull();
        assertThat(r.proximoCursor()).isNotNull();
    }
}
