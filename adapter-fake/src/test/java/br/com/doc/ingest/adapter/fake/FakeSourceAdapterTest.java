package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.adapter.*;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(resultado.ultNsu()).isEqualTo(nsu(2));
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
            assertThat(resultado.ultNsu()).isEqualTo(nsu(0));
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
        @DisplayName("buscarUm devolve o mesmo BlobRef do buscarLote")
        void buscarUmDevolveOMesmoBlobRefDoLote() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);
            BlobRef ref = new BlobRef("fake/7.xml", "a".repeat(64));
            Documento doc = new Documento(UUID.randomUUID(), fonte, cnpj, "55", nsu(7), Instant.EPOCH,
                    "COMPLETO", Optional.empty(), Optional.of(ref));
            adapter.preCarregar(cnpj, List.of(doc));

            Optional<BlobRef> doLote = adapter.buscarLote(new ContextoConsulta(fonte, cnpj), Cursor.inicial())
                    .documentos().get(0).blobRef();
            Optional<BlobRef> doUm = adapter.buscarUm(fonte, cnpj, nsu(7)).documento().orElseThrow().blobRef();

            assertThat(doLote).contains(ref);
            assertThat(doUm).isEqualTo(doLote);
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

    @Nested
    @DisplayName("cursor (DA-19)")
    class CursorDoFake {

        @Test
        @DisplayName("retomada pelo proximoCursor so traz NSU maior")
        void retomadaPeloProximoCursorSoTrazNsuMaior() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);
            adapter.preCarregar(cnpj, List.of(
                    documento(nsu(1), "RESUMO"), documento(nsu(2), "RESUMO"), documento(nsu(3), "RESUMO")));
            ContextoConsulta ctx = new ContextoConsulta(fonte, cnpj);

            BuscarLoteResultado primeiro = adapter.buscarLote(ctx, Cursor.inicial());
            assertThat(primeiro.desfecho()).isInstanceOf(Desfecho.Ok.class);
            assertThat(primeiro.documentos()).hasSize(3);
            assertThat(primeiro.proximoCursor()).isEqualTo(new Cursor("3"));

            BuscarLoteResultado segundo = adapter.buscarLote(ctx, primeiro.proximoCursor());
            assertThat(segundo.desfecho()).isInstanceOf(Desfecho.Vazio.class);
            assertThat(segundo.documentos()).isEmpty();
            assertThat(segundo.ultNsu()).isEqualTo(nsu(3));
            assertThat(segundo.proximoCursor()).isEqualTo(new Cursor("3"));
        }

        @Test
        @DisplayName("cursor nao numerico e rejeitado")
        void cursorNaoNumericoEhRejeitado() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);
            ContextoConsulta ctx = new ContextoConsulta(fonte, cnpj);

            assertThatThrownBy(() -> adapter.buscarLote(ctx, new Cursor("abc")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("desfecho programado")
    class DesfechoProgramado {

        @Test
        @DisplayName("desfecho programado nao avanca e pode ser removido")
        void desfechoProgramadoNaoAvanca() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);
            adapter.preCarregar(cnpj, List.of(documento(nsu(1), "RESUMO"), documento(nsu(2), "RESUMO")));
            ContextoConsulta ctx = new ContextoConsulta(fonte, cnpj);
            Cursor entrada = new Cursor("1");
            Desfecho falha = new Desfecho.IndisponivelTemporario(java.time.Duration.ofMinutes(5));

            adapter.programarDesfecho(cnpj, falha);
            BuscarLoteResultado r = adapter.buscarLote(ctx, entrada);
            assertThat(r.desfecho()).isEqualTo(falha);
            assertThat(r.documentos()).isEmpty();
            assertThat(r.proximoCursor()).isEqualTo(entrada);
            assertThat(r.ultNsu()).isEqualTo(nsu(1));
            assertThat(r.temMais()).isFalse();

            adapter.programarDesfecho(cnpj, null);
            BuscarLoteResultado normal = adapter.buscarLote(ctx, entrada);
            assertThat(normal.desfecho()).isInstanceOf(Desfecho.Ok.class);
            assertThat(normal.documentos()).hasSize(1);
        }

        @Test
        @DisplayName("programar Ok ou Vazio e rejeitado")
        void programarOkOuVazioEhRejeitado() {
            FakeSourceAdapter adapter = new FakeSourceAdapter(fonte);

            assertThatThrownBy(() -> adapter.programarDesfecho(cnpj, new Desfecho.Ok()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> adapter.programarDesfecho(cnpj, new Desfecho.Vazio()))
                    .isInstanceOf(IllegalArgumentException.class);
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
