package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.jdbi.v3.core.Jdbi;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades da rotação (inv. 1, §7.4): trocas de certificado intercaladas com confirmações
 * de lote nunca mudam cursor, {@code ult_nsu} nem {@code versao}; o cursor nunca retrocede.
 */
class EstadoColetaTrocaPropriedadesTest extends PostgresTestBase {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ChaveColeta CHAVE =
            new ChaveColeta(new FonteId("NFE_AN"), new Cnpj("11222333000181"), Ambiente.PRODUCAO);

    private static Jdbi jdbi;
    private static JdbiEstadoColeta repositorio;

    /** Passo da sequência: confirmar lote até {@code novoUltNsu}, ou trocar para {@code certificado}. */
    sealed interface Passo {
        record Confirmar(long novoUltNsu) implements Passo { }
        record Trocar(long certificado, boolean esperadoCorreto) implements Passo { }
    }

    private static synchronized void garantePronto() {
        if (jdbi == null) {
            var ds = dataSourceNoSchema("estado_coleta_troca_propriedades");
            Migracoes.aplicar(ds);
            jdbi = Jdbi.create(ds);
            repositorio = new JdbiEstadoColeta(jdbi, RELOGIO);
        }
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        jdbi.useHandle(h -> h.execute("truncate ledger_nsu, estado_coleta cascade"));
        repositorio.criarSeAusente(CHAVE);
    }

    @Provide
    Arbitrary<List<Passo>> passos() {
        Arbitrary<Passo> confirmar = Arbitraries.longs().between(0, 40).map(Passo.Confirmar::new);
        Arbitrary<Passo> trocar = Combinators.combine(
                Arbitraries.longs().between(1, 5), Arbitraries.of(true, false)).as(Passo.Trocar::new);
        return Arbitraries.oneOf(confirmar, trocar).list().ofMinSize(1).ofMaxSize(15);
    }

    @Property(tries = 15)
    void trocasAleatoriasNuncaMudamCursorNemVersao(@ForAll("passos") List<Passo> passos) {
        for (var passo : passos) {
            var antes = repositorio.carregar(CHAVE).orElseThrow();
            if (passo instanceof Passo.Trocar t) {
                Long atual = certificadoId();
                Long esperado = t.esperadoCorreto() ? atual : Long.valueOf(atual == null ? 99L : atual + 100);
                repositorio.trocarCertificado(CHAVE, esperado, t.certificado());
                assertThat(repositorio.carregar(CHAVE)).as("troca não mexe na posição").contains(antes);
            } else if (passo instanceof Passo.Confirmar c) {
                Long certificadoAntes = certificadoId();
                var lote = new ConfirmacaoLote(antes, new Cursor(Long.toString(c.novoUltNsu())), new Nsu(c.novoUltNsu()),
                        c.novoUltNsu() == 0 ? List.of()
                                : List.of(new EntradaLedger(new Nsu(c.novoUltNsu()), StatusNsu.BAIXADO)));
                var resultado = repositorio.confirmarLote(lote);
                assertThat(certificadoId()).as("confirmar não mexe no certificado").isEqualTo(certificadoAntes);
                if (resultado instanceof ResultadoCas.Aplicado) {
                    assertThat(repositorio.carregar(CHAVE).orElseThrow().versao()).isEqualTo(antes.versao() + 1);
                }
            }
        }
    }

    @Property(tries = 15)
    void cursorNuncaRetrocede(@ForAll("passos") List<Passo> passos) {
        var observados = new ArrayList<Long>();
        observados.add(repositorio.carregar(CHAVE).orElseThrow().ultNsu().valor());
        for (var passo : passos) {
            var antes = repositorio.carregar(CHAVE).orElseThrow();
            if (passo instanceof Passo.Trocar t) {
                repositorio.trocarCertificado(CHAVE, certificadoId(), t.certificado());
            } else if (passo instanceof Passo.Confirmar c) {
                repositorio.confirmarLote(new ConfirmacaoLote(antes, new Cursor(Long.toString(c.novoUltNsu())),
                        new Nsu(c.novoUltNsu()), List.of()));
            }
            observados.add(repositorio.carregar(CHAVE).orElseThrow().ultNsu().valor());
        }
        for (int i = 1; i < observados.size(); i++) {
            assertThat(observados.get(i)).as("passo %d", i).isGreaterThanOrEqualTo(observados.get(i - 1));
        }
    }

    private static Long certificadoId() {
        return jdbi.withHandle(h -> h.createQuery("select certificado_id from estado_coleta")
                .mapTo(Long.class).findOne().orElse(null));
    }
}
