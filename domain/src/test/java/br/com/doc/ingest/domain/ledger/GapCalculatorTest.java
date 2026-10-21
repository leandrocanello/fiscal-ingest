package br.com.doc.ingest.domain.ledger;

import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do GapCalculator — lógica PURA de cálculo de buracos (gaps)
 * na sequência de NSUs do ledger.
 *
 * Regras:
 * - Dado um conjunto de EntradaLedger, calcular quais NSUs estão faltando.
 * - Status DESCARTADO/FORA_PRAZO preenchem a sequência (não são buracos).
 * - O teto é o maior NSU conhecido (nunca reportar gaps acima do conhecido).
 * - Ledger vazio retorna lista vazia (decisão ADR-005).
 * - Não reportar gaps antes do primeiro NSU conhecido (fora do radar).
 */
@DisplayName("GapCalculator (cálculo de buracos no NSU Ledger)")
class GapCalculatorTest {

    private final GapCalculator calculator = new GapCalculator();

    @Nested
    @DisplayName("Casos básicos")
    class CasosBasicos {

        @Test
        @DisplayName("deve calcular gap quando sequência tem buraco: [1,2,5,6] → [3,4]")
        void deveCalcularGapComBuraco() {
            var entradas = List.of(
                    entrada(1, StatusNsu.BAIXADO),
                    entrada(2, StatusNsu.BAIXADO),
                    entrada(5, StatusNsu.RESUMO),
                    entrada(6, StatusNsu.BAIXADO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            assertThat(gaps).containsExactlyInAnyOrder(new Nsu(3), new Nsu(4));
        }

        @Test
        @DisplayName("deve retornar vazio quando sequência é contínua: [1,2,3] → []")
        void deveRetornarVazioQuandoContinua() {
            var entradas = List.of(
                    entrada(1, StatusNsu.BAIXADO),
                    entrada(2, StatusNsu.RESUMO),
                    entrada(3, StatusNsu.BAIXADO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            assertThat(gaps).isEmpty();
        }

        @Test
        @DisplayName("deve retornar vazio quando ledger está vazio (ADR-005)")
        void deveRetornarVazioParaLedgerVazio() {
            Set<Nsu> gaps = calculator.calcular(List.of());
            assertThat(gaps).isEmpty();
        }

        @Test
        @DisplayName("deve retornar vazio para entrada única")
        void deveRetornarVazioParaEntradaUnica() {
            var entradas = List.of(entrada(100, StatusNsu.BAIXADO));
            Set<Nsu> gaps = calculator.calcular(entradas);
            assertThat(gaps).isEmpty();
        }
    }

    @Nested
    @DisplayName("Status que preenchem a sequência (não são gaps)")
    class StatusQuePreenchem {

        @Test
        @DisplayName("NSUs DESCARTADO não devem gerar gap falso")
        void descartadoNaoEhGap() {
            var entradas = List.of(
                    entrada(1, StatusNsu.BAIXADO),
                    entrada(2, StatusNsu.DESCARTADO),
                    entrada(3, StatusNsu.BAIXADO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            assertThat(gaps).isEmpty();
        }

        @Test
        @DisplayName("NSUs FORA_PRAZO não devem gerar gap falso")
        void foraDoPrazoNaoEhGap() {
            var entradas = List.of(
                    entrada(1, StatusNsu.BAIXADO),
                    entrada(2, StatusNsu.FORA_PRAZO),
                    entrada(3, StatusNsu.BAIXADO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            assertThat(gaps).isEmpty();
        }

        @Test
        @DisplayName("todos os status válidos preenchem a sequência")
        void todosStatusPreenchem() {
            var entradas = List.of(
                    entrada(1, StatusNsu.BAIXADO),
                    entrada(2, StatusNsu.RESUMO),
                    entrada(3, StatusNsu.EVENTO),
                    entrada(4, StatusNsu.DESCARTADO),
                    entrada(5, StatusNsu.FORA_PRAZO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            assertThat(gaps).isEmpty();
        }
    }

    @Nested
    @DisplayName("Limites e teto")
    class LimitesETeto {

        @Test
        @DisplayName("não deve reportar gaps acima do maior NSU conhecido")
        void naoDeveReportarGapsAcimaDoTeto() {
            var entradas = List.of(
                    entrada(10, StatusNsu.BAIXADO),
                    entrada(15, StatusNsu.BAIXADO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            // Gaps entre 10 e 15: 11,12,13,14 — todos abaixo do teto (15)
            assertThat(gaps).containsExactlyInAnyOrder(
                    new Nsu(11), new Nsu(12), new Nsu(13), new Nsu(14));
        }

        @Test
        @DisplayName("não deve reportar gaps antes do primeiro NSU conhecido")
        void naoDeveReportarAntesDoPrimeiro() {
            // Primeiro NSU = 100 → não sabemos o que veio antes (fora do radar)
            var entradas = List.of(
                    entrada(100, StatusNsu.BAIXADO),
                    entrada(105, StatusNsu.BAIXADO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            // Só gaps entre 100 e 105: 101,102,103,104
            assertThat(gaps).containsExactlyInAnyOrder(
                    new Nsu(101), new Nsu(102), new Nsu(103), new Nsu(104));
        }

        @Test
        @DisplayName("deve calcular gap com NSUs fora de ordem no input")
        void deveCalcularComInputForaDeOrdem() {
            var entradas = List.of(
                    entrada(6, StatusNsu.BAIXADO),
                    entrada(1, StatusNsu.BAIXADO),
                    entrada(2, StatusNsu.BAIXADO),
                    entrada(5, StatusNsu.RESUMO)
            );

            Set<Nsu> gaps = calculator.calcular(entradas);

            assertThat(gaps).containsExactlyInAnyOrder(new Nsu(3), new Nsu(4));
        }
    }

    // --- Helper para criar entradas de teste concisas ---
    private static EntradaLedger entrada(long nsu, StatusNsu status) {
        return new EntradaLedger(new Nsu(nsu), status);
    }
}
