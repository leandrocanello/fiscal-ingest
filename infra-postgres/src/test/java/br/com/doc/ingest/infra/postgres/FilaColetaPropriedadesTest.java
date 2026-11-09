package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.PedidoReivindicacao;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades da fila de coleta (CLAUDE.md §15 item 2, inv. 1 e 2): sequências aleatórias de
 * reivindicar, renovar, liberar, bloquear e avançar o relógio, com vários donos, contra um modelo em memória.
 */
class FilaColetaPropriedadesTest extends PostgresTestBase {

    private static final Instant INICIO = Instant.parse("2026-01-01T12:00:00Z");
    private static final int CHAVES = 4;
    private static final String[] DONOS = {"A", "B", "C"};
    private static final FonteId FONTE = new FonteId("NFE_AN");

    private static Jdbi jdbi;
    private static RelogioManual relogio;
    private static JdbiFilaColeta fila;

    enum Tipo { REIVINDICAR, RENOVAR, LIBERAR, BLOQUEAR, AVANCAR }

    record Operacao(Tipo tipo, int chave, int dono, int minutos) { }

    /** Modelo de uma chave: o que a fila deveria ter gravado. */
    private static final class Modelo {
        Instant dueAt;
        Instant bloqueadoAte;
        String dono;
        Instant leaseAte;
    }

    record Execucao(List<String> leaseVigenteReivindicado, List<String> bloqueioEncurtado,
                    List<String> divergenciasDoModelo, Set<Integer> reivindicadasNoFim,
                    List<String> cursorOuVersaoAlterados) { }

    private static synchronized void garantePronto() {
        if (jdbi == null) {
            var ds = dataSourceNoSchema("fila_coleta_propriedades");
            Migracoes.aplicar(ds);
            jdbi = Jdbi.create(ds);
            relogio = new RelogioManual(INICIO);
            fila = new JdbiFilaColeta(jdbi, relogio);
        }
    }

    @BeforeTry
    void limpa() {
        garantePronto();
        relogio.definir(INICIO);
        jdbi.useHandle(h -> {
            h.execute("truncate estado_coleta cascade");
            for (int i = 0; i < CHAVES; i++) {
                h.createUpdate("""
                                insert into estado_coleta (fonte, cnpj_consultado, ambiente, cursor_token, ult_nsu,
                                                           versao, estado, atualizado_em)
                                values (:fonte, :cnpj, 'PRODUCAO', '0', 0, 0, 'ATIVO', :agora)
                                """)
                        .bind("fonte", FONTE.id())
                        .bind("cnpj", cnpj(i))
                        .bind("agora", Timestamp.from(INICIO))
                        .execute();
            }
        });
    }

    private static String cnpj(int i) {
        return String.format("%014d", i);
    }

    private static ChaveColeta chave(int i) {
        return new ChaveColeta(FONTE, new Cnpj(cnpj(i)), Ambiente.PRODUCAO);
    }

    @Provide
    Arbitrary<List<Operacao>> operacoes() {
        return Combinators.combine(
                        Arbitraries.of(Tipo.class),
                        Arbitraries.integers().between(0, CHAVES - 1),
                        Arbitraries.integers().between(0, DONOS.length - 1),
                        Arbitraries.integers().between(1, 30))
                .as(Operacao::new)
                .list().ofMinSize(1).ofMaxSize(25);
    }

    @Property(tries = 15)
    void chaveComLeaseVigenteNuncaEhReivindicada(@ForAll("operacoes") List<Operacao> operacoes) {
        assertThat(executar(operacoes).leaseVigenteReivindicado()).isEmpty();
    }

    @Property(tries = 15)
    void chaveVencidaELivreSempreAcabaReivindicada(@ForAll("operacoes") List<Operacao> operacoes) {
        assertThat(executar(operacoes).reivindicadasNoFim()).hasSize(CHAVES);
    }

    @Property(tries = 15)
    void bloqueadoAteNuncaEncurta(@ForAll("operacoes") List<Operacao> operacoes) {
        assertThat(executar(operacoes).bloqueioEncurtado()).isEmpty();
    }

    @Property(tries = 15)
    void claimNuncaMudaCursorNemVersao(@ForAll("operacoes") List<Operacao> operacoes) {
        assertThat(executar(operacoes).cursorOuVersaoAlterados()).isEmpty();
    }

    @Property(tries = 15)
    void bancoSegueOModeloDeLeaseEDue(@ForAll("operacoes") List<Operacao> operacoes) {
        assertThat(executar(operacoes).divergenciasDoModelo()).isEmpty();
    }

    private static Execucao executar(List<Operacao> operacoes) {
        var modelo = new Modelo[CHAVES];
        for (int i = 0; i < CHAVES; i++) {
            modelo[i] = new Modelo();
        }
        var leaseVigente = new ArrayList<String>();
        var encurtados = new ArrayList<String>();
        var divergencias = new ArrayList<String>();
        var alterados = new ArrayList<String>();
        var bloqueioVisto = new Instant[CHAVES];

        for (int passo = 0; passo < operacoes.size(); passo++) {
            var op = operacoes.get(passo);
            var agora = relogio.instant();
            var dono = DONOS[op.dono()];
            var duracao = Duration.ofMinutes(op.minutos());
            switch (op.tipo()) {
                case REIVINDICAR -> {
                    var resposta = fila.reivindicar(new PedidoReivindicacao(dono, duracao, 2));
                    for (var c : resposta) {
                        int i = Integer.parseInt(c.posicao().chave().cnpj().valor());
                        var m = modelo[i];
                        if (m.leaseAte != null && m.leaseAte.isAfter(agora)) {
                            leaseVigente.add("passo " + passo + " chave " + i);
                        }
                        m.dono = dono;
                        m.leaseAte = agora.plus(duracao);
                    }
                }
                case RENOVAR -> {
                    var m = modelo[op.chave()];
                    boolean esperado = dono.equals(m.dono);
                    boolean obtido = fila.renovar(chave(op.chave()), dono, duracao);
                    if (esperado != obtido) {
                        divergencias.add("passo " + passo + " renovar");
                    }
                    if (esperado) {
                        m.leaseAte = agora.plus(duracao);
                    }
                }
                case LIBERAR -> {
                    var m = modelo[op.chave()];
                    boolean esperado = dono.equals(m.dono);
                    boolean obtido = fila.liberar(chave(op.chave()), dono, agora.plus(duracao));
                    if (esperado != obtido) {
                        divergencias.add("passo " + passo + " liberar");
                    }
                    if (esperado) {
                        m.dono = null;
                        m.leaseAte = null;
                        m.dueAt = agora.plus(duracao);
                    }
                }
                case BLOQUEAR -> {
                    var m = modelo[op.chave()];
                    var ate = agora.plus(duracao);
                    fila.registrarBloqueio(chave(op.chave()), ate);
                    m.bloqueadoAte = maior(m.bloqueadoAte, ate);
                    m.dueAt = maior(maior(m.dueAt, m.bloqueadoAte), ate);
                }
                case AVANCAR -> relogio.avancar(duracao);
            }
            for (int i = 0; i < CHAVES; i++) {
                var linha = ler(i);
                if (bloqueioVisto[i] != null && (linha.bloqueadoAte() == null
                        || linha.bloqueadoAte().isBefore(bloqueioVisto[i]))) {
                    encurtados.add("passo " + passo + " chave " + i);
                }
                bloqueioVisto[i] = linha.bloqueadoAte();
                if (!java.util.Objects.equals(linha.dueAt(), modelo[i].dueAt)
                        || !java.util.Objects.equals(linha.bloqueadoAte(), modelo[i].bloqueadoAte)
                        || !java.util.Objects.equals(linha.leaseAte(), modelo[i].leaseAte)) {
                    divergencias.add("passo " + passo + " chave " + i + " " + linha);
                }
                if (!"0".equals(linha.cursor()) || linha.ultNsu() != 0 || linha.versao() != 0) {
                    alterados.add("passo " + passo + " chave " + i);
                }
            }
        }

        // Sem fome: com tudo vencido, ciclos de claim esgotam todas as chaves.
        relogio.avancar(Duration.ofHours(24));
        var noFim = new HashSet<Integer>();
        for (int ciclo = 0; ciclo < CHAVES; ciclo++) {
            for (var c : fila.reivindicar(new PedidoReivindicacao("Z", Duration.ofHours(1), 2))) {
                noFim.add(Integer.parseInt(c.posicao().chave().cnpj().valor()));
            }
        }
        return new Execucao(leaseVigente, encurtados, divergencias, noFim, alterados);
    }

    private static Instant maior(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private record Linha(Instant dueAt, Instant bloqueadoAte, Instant leaseAte, String cursor, long ultNsu,
                         long versao) { }

    private static Linha ler(int i) {
        return jdbi.withHandle(h -> h.createQuery("""
                        select due_at, bloqueado_ate, lease_ate, cursor_token, ult_nsu, versao
                        from estado_coleta where cnpj_consultado = :c
                        """)
                .bind("c", cnpj(i))
                .map((rs, ctx) -> new Linha(instante(rs.getTimestamp("due_at")),
                        instante(rs.getTimestamp("bloqueado_ate")), instante(rs.getTimestamp("lease_ate")),
                        rs.getString("cursor_token"), rs.getLong("ult_nsu"), rs.getLong("versao")))
                .one());
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
