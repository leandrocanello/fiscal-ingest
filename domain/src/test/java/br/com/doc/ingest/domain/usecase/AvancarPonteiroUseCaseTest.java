package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.NsuLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do AvancarPonteiroUseCase — orquestra o avanço do cursor
 * com base no Desfecho retornado pelo adapter.
 *
 * Regras críticas:
 * - Ok/Vazio → avança cursor + persiste entradas no ledger
 * - LimiteAtingido → NÃO avança (cStat 656/632 — retrocesso proibido)
 * - IndisponivelTemporario → NÃO avança (retry sem mover ponteiro)
 */
@DisplayName("AvancarPonteiroUseCase (controle de cursor)")
class AvancarPonteiroUseCaseTest {

    private final FonteId fonte = new FonteId("NFE_AN");
    private final Cnpj cnpj = new Cnpj("12345678000199");

    @Nested
    @DisplayName("Avanço do cursor")
    class AvancoDoCursor {

        @Test
        @DisplayName("deve avançar cursor quando Desfecho é Ok")
        void deveAvancarCursorEmOk() {
            Cursor cursorInicial = new Cursor("bookmark-100");
            NsuLedgerFake ledger = new NsuLedgerFake();
            AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(ledger);

            BuscarLoteResultado resultado = new BuscarLoteResultado(
                    List.of(),
                    new Cursor("bookmark-105"),
                    new Nsu(105),
                    false,
                    new Desfecho.Ok()
            );

            ResultadoAvanco avanco = useCase.executar(fonte, cnpj, cursorInicial, new Nsu(100), resultado);

            assertThat(avanco.cursorAtual()).isEqualTo(new Cursor("bookmark-105"));
            assertThat(avanco.ultNsu()).isEqualTo(new Nsu(105));
            assertThat(avanco.avancou()).isTrue();
        }

        @Test
        @DisplayName("deve avançar cursor quando Desfecho é Vazio")
        void deveAvancarCursorEmVazio() {
            Cursor cursorInicial = new Cursor("bookmark-100");
            NsuLedgerFake ledger = new NsuLedgerFake();
            AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(ledger);

            BuscarLoteResultado resultado = new BuscarLoteResultado(
                    List.of(),
                    new Cursor("bookmark-101"),
                    new Nsu(101),
                    false,
                    new Desfecho.Vazio()
            );

            ResultadoAvanco avanco = useCase.executar(fonte, cnpj, cursorInicial, new Nsu(100), resultado);

            assertThat(avanco.avancou()).isTrue();
            assertThat(avanco.cursorAtual()).isEqualTo(new Cursor("bookmark-101"));
            assertThat(avanco.ultNsu()).isEqualTo(new Nsu(101));
        }
    }

    @Nested
    @DisplayName("NÃO avançar (proteção cStat 656)")
    class NaoAvancar {

        @Test
        @DisplayName("NÃO deve avançar cursor quando Desfecho é LimiteAtingido")
        void naoDeveAvancarEmLimiteAtingido() {
            Cursor cursorInicial = new Cursor("bookmark-100");
            NsuLedgerFake ledger = new NsuLedgerFake();
            AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(ledger);

            BuscarLoteResultado resultado = new BuscarLoteResultado(
                    List.of(),
                    new Cursor("bookmark-999"),  // adapter pode ter retornado cursor, mas ignoramos
                    new Nsu(999),
                    false,
                    new Desfecho.LimiteAtingido(Duration.ofHours(1))
            );

            ResultadoAvanco avanco = useCase.executar(fonte, cnpj, cursorInicial, new Nsu(100), resultado);

            assertThat(avanco.avancou()).isFalse();
            assertThat(avanco.cursorAtual()).isEqualTo(cursorInicial); // mantém o original
            assertThat(avanco.ultNsu()).isEqualTo(new Nsu(100));
        }

        @Test
        @DisplayName("NÃO deve avançar cursor quando Desfecho é IndisponivelTemporario")
        void naoDeveAvancarEmIndisponivelTemporario() {
            Cursor cursorInicial = new Cursor("bookmark-100");
            NsuLedgerFake ledger = new NsuLedgerFake();
            AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(ledger);

            BuscarLoteResultado resultado = new BuscarLoteResultado(
                    List.of(),
                    new Cursor("bookmark-999"),
                    new Nsu(999),
                    false,
                    new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5))
            );

            ResultadoAvanco avanco = useCase.executar(fonte, cnpj, cursorInicial, new Nsu(100), resultado);

            assertThat(avanco.avancou()).isFalse();
            assertThat(avanco.cursorAtual()).isEqualTo(cursorInicial);
            assertThat(avanco.ultNsu()).isEqualTo(new Nsu(100));
        }
    }

    @Nested
    @DisplayName("Persistência no ledger")
    class PersistenciaLedger {

        @Test
        @DisplayName("deve registrar cada NSU retornado no ledger quando Ok")
        void deveRegistrarNsuNoLedger() {
            NsuLedgerFake ledger = new NsuLedgerFake();
            AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(ledger);

            List<Documento> docs = List.of(
                    doc(1, "RESUMO"),
                    doc(2, "COMPLETO"),
                    doc(3, "EVENTO")
            );

            BuscarLoteResultado resultado = new BuscarLoteResultado(
                    docs,
                    new Cursor("bookmark-200"),
                    new Nsu(200),
                    false,
                    new Desfecho.Ok()
            );

            useCase.executar(fonte, cnpj, Cursor.inicial(), new Nsu(0), resultado);

            assertThat(ledger.registrados).hasSize(3);
            assertThat(ledger.registrados.get(0).nsu()).isEqualTo(new Nsu(1));
            assertThat(ledger.registrados.get(0).status()).isEqualTo(StatusNsu.RESUMO);
            assertThat(ledger.registrados.get(1).nsu()).isEqualTo(new Nsu(2));
            assertThat(ledger.registrados.get(1).status()).isEqualTo(StatusNsu.BAIXADO);
            assertThat(ledger.registrados.get(2).nsu()).isEqualTo(new Nsu(3));
            assertThat(ledger.registrados.get(2).status()).isEqualTo(StatusNsu.EVENTO);
        }

        @Test
        @DisplayName("NÃO deve registrar nada no ledger quando LimiteAtingido")
        void naoDeveRegistrarEmLimiteAtingido() {
            NsuLedgerFake ledger = new NsuLedgerFake();
            AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(ledger);

            BuscarLoteResultado resultado = new BuscarLoteResultado(
                    List.of(doc(1, "RESUMO")),  // mesmo com doc, não registra
                    new Cursor("bookmark-200"),
                    new Nsu(200),
                    false,
                    new Desfecho.LimiteAtingido(Duration.ofHours(1))
            );

            useCase.executar(fonte, cnpj, Cursor.inicial(), new Nsu(0), resultado);

            assertThat(ledger.registrados).isEmpty();
        }
    }

    // --- Helpers ---

    private static Documento doc(long nsu, String tipoConteudo) {
        return new Documento(
                java.util.UUID.randomUUID(),
                null, null, null,
                new Nsu(nsu),
                Instant.now(),
                tipoConteudo,
                Optional.empty(),
                Optional.empty()
        );
    }

    /** Implementação fake do NsuLedger para testes do core. */
    private static class NsuLedgerFake implements NsuLedger {
        final List<EntradaLedger> registrados = new ArrayList<>();

        @Override
        public void registrar(FonteId fonte, Cnpj cnpj, EntradaLedger entrada) {
            registrados.add(entrada);
        }

        @Override
        public List<EntradaLedger> listarEntradas(FonteId fonte, Cnpj cnpj) {
            return List.copyOf(registrados);
        }
    }
}
