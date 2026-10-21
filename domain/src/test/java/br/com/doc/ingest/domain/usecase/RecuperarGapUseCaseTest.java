package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.BuscarUmResultado;
import br.com.doc.ingest.domain.adapter.ConsultaPontualPort;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.NsuLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do RecuperarGapUseCase - orquestra a recuperacao de NSUs faltantes.
 *
 * Regras:
 * - Usa GapCalculator para identificar buracos na sequencia.
 * - Chama buscarUm (consulta pontual) para cada NSU faltante.
 * - Marca gap como FORA_PRAZO se a origem retornar IndisponivelDefinitivo.
 * - Marca gap como BAIXADO/RESUMO se a origem retornar documento com Ok.
 * - Se LimiteAtingido, para e devolve NSUs restantes para retry.
 */
@DisplayName("RecuperarGapUseCase (recuperacao de buracos)")
class RecuperarGapUseCaseTest {

    private final FonteId fonte = new FonteId("NFE_AN");
    private final Cnpj cnpj = new Cnpj("12345678000199");

    @Nested
    @DisplayName("Recuperacao basica")
    class RecuperacaoBasica {

        @Test
        @DisplayName("deve chamar buscarUm para cada NSU faltante")
        void deveChamarBuscarUmParaCadaNsuFaltante() {
            NsuLedgerFake ledger = new NsuLedgerFake();
            ledger.adicionar(new EntradaLedger(new Nsu(1), StatusNsu.BAIXADO));
            ledger.adicionar(new EntradaLedger(new Nsu(2), StatusNsu.BAIXADO));
            ledger.adicionar(new EntradaLedger(new Nsu(5), StatusNsu.BAIXADO));
            // Gaps: 3, 4

            ConsultaPontualFake consulta = new ConsultaPontualFake();
            consulta.responder(new Nsu(3), documentoOk(new Nsu(3)));
            consulta.responder(new Nsu(4), documentoOk(new Nsu(4)));

            RecuperarGapUseCase useCase = new RecuperarGapUseCase(ledger, consulta);

            ResultadoRecuperacao resultado = useCase.executar(fonte, cnpj);

            assertThat(consulta.chamados).containsExactly(new Nsu(3), new Nsu(4));
            assertThat(resultado.recuperados()).hasSize(2);
            assertThat(resultado.pendentes()).isEmpty();
        }

        @Test
        @DisplayName("nao deve chamar buscarUm quando nao ha gaps")
        void naoDeveChamarBuscarUmSemGaps() {
            NsuLedgerFake ledger = new NsuLedgerFake();
            ledger.adicionar(new EntradaLedger(new Nsu(1), StatusNsu.BAIXADO));
            ledger.adicionar(new EntradaLedger(new Nsu(2), StatusNsu.BAIXADO));
            ledger.adicionar(new EntradaLedger(new Nsu(3), StatusNsu.BAIXADO));

            ConsultaPontualFake consulta = new ConsultaPontualFake();
            RecuperarGapUseCase useCase = new RecuperarGapUseCase(ledger, consulta);

            ResultadoRecuperacao resultado = useCase.executar(fonte, cnpj);

            assertThat(consulta.chamados).isEmpty();
            assertThat(resultado.recuperados()).isEmpty();
            assertThat(resultado.pendentes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Tratamento de desfechos")
    class TratamentoDesfechos {

        @Test
        @DisplayName("deve marcar como FORA_PRAZO quando origem retorna IndisponivelDefinitivo")
        void deveMarcarForaDoPrazoEmIndisponivelDefinitivo() {
            NsuLedgerFake ledger = new NsuLedgerFake();
            ledger.adicionar(new EntradaLedger(new Nsu(1), StatusNsu.BAIXADO));
            ledger.adicionar(new EntradaLedger(new Nsu(3), StatusNsu.BAIXADO));
            // Gap: 2

            ConsultaPontualFake consulta = new ConsultaPontualFake();
            consulta.responder(new Nsu(2),
                    new BuscarUmResultado(java.util.Optional.empty(),
                            new Desfecho.IndisponivelDefinitivo("fora do prazo")));

            RecuperarGapUseCase useCase = new RecuperarGapUseCase(ledger, consulta);
            ResultadoRecuperacao resultado = useCase.executar(fonte, cnpj);

            assertThat(resultado.recuperados()).isEmpty();
            assertThat(resultado.foraDoPrazo()).containsExactly(new Nsu(2));
            // Deve registrar FORA_PRAZO no ledger
            assertThat(ledger.registrados).anyMatch(e ->
                    e.nsu().equals(new Nsu(2)) && e.status() == StatusNsu.FORA_PRAZO);
        }

        @Test
        @DisplayName("deve parar e devolver pendentes quando LimiteAtingido")
        void devePararEDevolverPendentesEmLimiteAtingido() {
            NsuLedgerFake ledger = new NsuLedgerFake();
            ledger.adicionar(new EntradaLedger(new Nsu(1), StatusNsu.BAIXADO));
            ledger.adicionar(new EntradaLedger(new Nsu(5), StatusNsu.BAIXADO));
            // Gaps: 2, 3, 4

            ConsultaPontualFake consulta = new ConsultaPontualFake();
            consulta.responder(new Nsu(2), documentoOk(new Nsu(2)));
            consulta.responder(new Nsu(3),
                    new BuscarUmResultado(java.util.Optional.empty(),
                            new Desfecho.LimiteAtingido(Duration.ofMinutes(3))));

            RecuperarGapUseCase useCase = new RecuperarGapUseCase(ledger, consulta);
            ResultadoRecuperacao resultado = useCase.executar(fonte, cnpj);

            // Recuperou o 2, parou no 3 (limite), 4 ficou pendente
            assertThat(resultado.recuperados()).containsExactly(new Nsu(2));
            assertThat(resultado.pendentes()).containsExactly(new Nsu(3), new Nsu(4));
        }
    }

    // --- Helpers ---

    private static BuscarUmResultado documentoOk(Nsu nsu) {
        return new BuscarUmResultado(
                java.util.Optional.of(new br.com.doc.ingest.domain.model.Documento(
                        java.util.UUID.randomUUID(),
                        null, null, null,
                        nsu,
                        java.time.Instant.now(),
                        "COMPLETO",
                        java.util.Optional.empty(),
                        java.util.Optional.empty()
                )),
                new Desfecho.Ok()
        );
    }

    /** Fake do NsuLedger para testes. */
    private static class NsuLedgerFake implements NsuLedger {
        final List<EntradaLedger> entradas = new ArrayList<>();
        final List<EntradaLedger> registrados = new ArrayList<>();

        void adicionar(EntradaLedger e) { entradas.add(e); }

        @Override
        public void registrar(FonteId fonte, Cnpj cnpj, EntradaLedger entrada) {
            registrados.add(entrada);
        }

        @Override
        public List<EntradaLedger> listarEntradas(FonteId fonte, Cnpj cnpj) {
            return List.copyOf(entradas);
        }
    }

    /** Fake da ConsultaPontualPort para testes. */
    private static class ConsultaPontualFake implements ConsultaPontualPort {
        final List<Nsu> chamados = new ArrayList<>();
        final java.util.Map<Nsu, BuscarUmResultado> respostas = new java.util.HashMap<>();

        void responder(Nsu nsu, BuscarUmResultado resultado) {
            respostas.put(nsu, resultado);
        }

        @Override
        public BuscarUmResultado buscarUm(FonteId fonte, Cnpj cnpj, Nsu nsu) {
            chamados.add(nsu);
            return respostas.getOrDefault(nsu,
                    new BuscarUmResultado(java.util.Optional.empty(), new Desfecho.Vazio()));
        }
    }
}
