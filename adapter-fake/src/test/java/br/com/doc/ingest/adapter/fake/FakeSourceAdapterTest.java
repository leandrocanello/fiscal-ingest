package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.adapter.*;
import br.com.doc.ingest.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do FakeSourceAdapter - adapter em memoria para testar o core sem SEFAZ.
 *
 * Cenarios testados:
 * - descritor retorna fonte + capacidades corretas
 * - buscarLote retorna documentos pre-configurados com Ok
 * - buscarLote retorna Vazio quando nao ha documentos
 * - buscarUm retorna documento especifico por NSU
 * - buscarUm retorna Vazio quando NSU nao existe
 */
@DisplayName("FakeSourceAdapter (adapter em memoria)")
class FakeSourceAdapterTest {

    private final FonteId fonte = new FonteId("NFE_AN");
    private final Cnpj cnpj = new Cnpj("12345678000199");

    @Nested
    @DisplayName("Descritor")
    class Descritor {

        @Test
        @DisplayName("deve retornar descritor com fonte e capacidades corretas")
        void deveRetornarDescritor() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);

            AdapterDescriptor descritor = adapter.descritor();

            assertThat(descritor.fonteId()).isEqualTo(fonte);
            assertThat(descritor.capacidades().fazLote()).isTrue();
            assertThat(descritor.capacidades().fazPontual()).isTrue();
            assertThat(descritor.capacidades().fazManifestacao()).isTrue();
            assertThat(descritor.capacidades().permiteRetrocesso()).isFalse();
        }
    }

    @Nested
    @DisplayName("buscarLote")
    class BuscarLote {

        @Test
        @DisplayName("deve retornar documentos pre-configurados com desfecho Ok")
        void deveRetornarDocumentosComOk() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);

            Documento doc1 = documento(nsu(1), "RESUMO");
            Documento doc2 = documento(nsu(2), "COMPLETO");
            adapter.preCarregar(cnpj, List.of(doc1, doc2));

            ContextoConsulta ctx = new ContextoConsulta(fonte, cnpj);
            BuscarLoteResultado resultado = adapter.buscarLote(ctx, Cursor.inicial());

            assertThat(resultado.desfecho()).isInstanceOf(Desfecho.Ok.class);
            assertThat(resultado.documentos()).hasSize(2);
            assertThat(resultado.documentos().get(0).nsu()).isEqualTo(nsu(1));
            assertThat(resultado.documentos().get(1).nsu()).isEqualTo(nsu(2));
            assertThat(resultado.temMais()).isFalse();
        }

        @Test
        @DisplayName("deve retornar Vazio quando nao ha documentos para o CNPJ")
        void deveRetornarVazioSemDocumentos() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);

            ContextoConsulta ctx = new ContextoConsulta(fonte, cnpj);
            BuscarLoteResultado resultado = adapter.buscarLote(ctx, Cursor.inicial());

            assertThat(resultado.desfecho()).isInstanceOf(Desfecho.Vazio.class);
            assertThat(resultado.documentos()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarUm")
    class BuscarUm {

        @Test
        @DisplayName("deve retornar documento quando NSU existe")
        void deveRetornarDocumentoQuandoNsuExiste() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);

            Documento doc = documento(nsu(5), "COMPLETO");
            adapter.preCarregar(cnpj, List.of(doc));

            BuscarUmResultado resultado = adapter.buscarUm(fonte, cnpj, nsu(5));

            assertThat(resultado.desfecho()).isInstanceOf(Desfecho.Ok.class);
            assertThat(resultado.documento()).isPresent();
            assertThat(resultado.documento().get().nsu()).isEqualTo(nsu(5));
        }

        @Test
        @DisplayName("deve retornar Vazio quando NSU nao existe")
        void deveRetornarVazioQuandoNsuNaoExiste() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);

            BuscarUmResultado resultado = adapter.buscarUm(fonte, cnpj, nsu(999));

            assertThat(resultado.desfecho()).isInstanceOf(Desfecho.Vazio.class);
            assertThat(resultado.documento()).isEmpty();
        }
    }

    // --- Helpers ---

    private static Nsu nsu(long valor) {
        return new Nsu(valor);
    }

    private static Documento documento(Nsu nsu, String tipoConteudo) {
        return new Documento(
                UUID.randomUUID(),
                null,
                null,
                null,
                nsu,
                Instant.now(),
                tipoConteudo,
                Optional.empty(),
                Optional.empty()
        );
    }
}
