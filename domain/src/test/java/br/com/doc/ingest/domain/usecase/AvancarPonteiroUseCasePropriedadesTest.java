package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.NsuLedger;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do {@link AvancarPonteiroUseCase} (CLAUDE.md §2.2, invariante 1 — não-retrocesso de NSU; ADR-026).
 *
 * <p>Em qualquer sequência de lotes, com qualquer {@link Desfecho} e qualquer {@code ultNsu} (menor, igual ou
 * maior que o atual), a posição persistida nunca decresce.
 */
class AvancarPonteiroUseCasePropriedadesTest {

    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("12345678000199");

    @Property
    void cursorNuncaRecua(@ForAll("sequenciasDeLotes") List<BuscarLoteResultado> lotes) {
        AvancarPonteiroUseCase useCase = new AvancarPonteiroUseCase(new NsuLedgerFake());
        Cursor cursorAtual = Cursor.inicial();
        Nsu ultNsuAtual = new Nsu(0);

        for (BuscarLoteResultado lote : lotes) {
            ResultadoAvanco avanco = useCase.executar(FONTE, CNPJ, cursorAtual, ultNsuAtual, lote);

            assertThat(avanco.ultNsu().ehMenorQue(ultNsuAtual))
                    .as("ultNsu persistido recuou de %s para %s", ultNsuAtual, avanco.ultNsu())
                    .isFalse();

            boolean okOuVazio = lote.desfecho() instanceof Desfecho.Ok || lote.desfecho() instanceof Desfecho.Vazio;
            boolean deveAvancar = okOuVazio && lote.ultNsu().ehMaiorQue(ultNsuAtual);
            assertThat(avanco.motivo() == MotivoAvanco.AVANCOU)
                    .as("AVANCOU <=> (Ok|Vazio) e novo > atual; lote=%s, atual=%s", lote, ultNsuAtual)
                    .isEqualTo(deveAvancar);

            cursorAtual = avanco.cursorAtual();
            ultNsuAtual = avanco.ultNsu();
        }
    }

    @Provide
    Arbitrary<List<BuscarLoteResultado>> sequenciasDeLotes() {
        return lotes().list().ofMinSize(1).ofMaxSize(50);
    }

    private Arbitrary<BuscarLoteResultado> lotes() {
        Arbitrary<Long> nsus = Arbitraries.longs().between(0L, 1_000_000L);
        return Combinators.combine(desfechos(), nsus, Arbitraries.of(true, false))
                .as((desfecho, nsu, temMais) -> new BuscarLoteResultado(
                        List.of(), new Cursor("token-" + nsu), new Nsu(nsu), temMais, desfecho));
    }

    private Arbitrary<Desfecho> desfechos() {
        Duration espera = Duration.ofMinutes(60);
        return Arbitraries.of(
                new Desfecho.Ok(),
                new Desfecho.Vazio(),
                new Desfecho.LimiteAtingido(espera),
                new Desfecho.IndisponivelTemporario(espera),
                new Desfecho.IndisponivelDefinitivo("motivo"),
                new Desfecho.ErroAutenticacao("motivo"),
                new Desfecho.ServicoFora("motivo"));
    }

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
