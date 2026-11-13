package br.com.doc.ingest.infra.postgres;

import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ColetaReivindicada;
import br.com.doc.ingest.domain.coleta.PedidoReivindicacao;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FixturesFiscais;
import br.com.doc.ingest.domain.model.FonteId;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caos (CLAUDE.md §15 item 7): várias instâncias do scheduler reivindicando ao mesmo tempo.
 * Nenhuma chave pode ter dois donos (inv. 2, primeira linha de defesa; o CAS segue como a última).
 */
class CaosClaimConcorrenteTest extends PostgresTestBase {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final Instant INICIO = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final String FONTE = "NFE_AN";

    private Jdbi jdbi;
    private RelogioManual relogio;
    private JdbiFilaColeta fila;

    @BeforeEach
    void prepara() {
        var ds = dataSourceNoSchema("caos_claim_" + CONTADOR.incrementAndGet());
        Migracoes.aplicar(ds);
        jdbi = Jdbi.create(ds);
        relogio = new RelogioManual(INICIO);
        fila = new JdbiFilaColeta(jdbi, relogio);
    }

    private static String cnpj(int i) {
        // base deslocada de 1: a base zerada daria 14 zeros, recusado mesmo com DV certo
        return FixturesFiscais.cnpj(String.format("%012d", i + 1));
    }

    private void insere(int i, Instant leaseAte) {
        jdbi.useHandle(h -> h.createUpdate("""
                        insert into estado_coleta (fonte, cnpj_consultado, ambiente, cursor_token, ult_nsu, versao,
                                                   estado, due_at, lease_owner, lease_ate, atualizado_em)
                        values (:fonte, :cnpj, 'PRODUCAO', '0', 0, 0, 'ATIVO', null, :dono, :leaseAte, :agora)
                        """)
                .bind("fonte", FONTE)
                .bind("cnpj", cnpj(i))
                .bind("dono", leaseAte == null ? null : "antigo")
                .bind("leaseAte", leaseAte == null ? null : Timestamp.from(leaseAte))
                .bind("agora", Timestamp.from(INICIO))
                .execute());
    }

    private List<List<ColetaReivindicada>> concorrer(int threads, int limite, int tentativasPorThread)
            throws Exception {
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var barreira = new CyclicBarrier(threads);
            List<Future<List<ColetaReivindicada>>> futuros = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String dono = "worker-" + t;
                Callable<List<ColetaReivindicada>> tarefa = () -> {
                    barreira.await(30, TimeUnit.SECONDS);
                    List<ColetaReivindicada> todas = new ArrayList<>();
                    for (int k = 0; k < tentativasPorThread; k++) {
                        todas.addAll(fila.reivindicar(new PedidoReivindicacao(dono, LEASE, limite)));
                    }
                    return todas;
                };
                futuros.add(pool.submit(tarefa));
            }
            List<List<ColetaReivindicada>> respostas = new ArrayList<>();
            for (var f : futuros) {
                respostas.add(f.get(60, TimeUnit.SECONDS));
            }
            return respostas;
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void nenhumaChaveTemDoisDonosAoMesmoTempo() throws Exception {
        int estados = 200;
        for (int i = 0; i < estados; i++) {
            insere(i, null);
        }

        // 8 threads x até 30 claims de 20 (sobra tentativa para esgotar as 200 chaves)
        var respostas = concorrer(8, 20, 30);

        Set<ChaveColeta> vistas = new HashSet<>();
        int total = 0;
        for (var resposta : respostas) {
            for (var coleta : resposta) {
                total++;
                assertThat(vistas.add(coleta.posicao().chave()))
                        .as("chave %s reivindicada duas vezes", coleta.posicao().chave())
                        .isTrue();
            }
        }
        assertThat(total).isEqualTo(estados);
        assertThat(vistas).hasSize(estados);
        int comDono = jdbi.withHandle(h -> h.createQuery(
                "select count(*) from estado_coleta where lease_owner is not null").mapTo(Integer.class).one());
        assertThat(comDono).isEqualTo(estados);
    }

    @Test
    void claimNaoEsperaLinhaTravada() throws Exception {
        for (int i = 0; i < 5; i++) {
            insere(i, null);
        }
        var travou = new java.util.concurrent.CountDownLatch(1);
        var soltar = new java.util.concurrent.CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> segurando = pool.submit(() -> jdbi.useTransaction(h -> {
                h.createQuery("select 1 from estado_coleta where cnpj_consultado = :c for update")
                        .bind("c", cnpj(0)).mapTo(Integer.class).one();
                travou.countDown();
                try {
                    soltar.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(travou.await(10, TimeUnit.SECONDS)).isTrue();

            var claim = Executors.newSingleThreadExecutor();
            try {
                var resposta = claim.submit(() -> fila.reivindicar(new PedidoReivindicacao("A", LEASE, 10)))
                        .get(10, TimeUnit.SECONDS);

                assertThat(resposta).extracting(c -> c.posicao().chave().cnpj().valor())
                        .containsExactlyInAnyOrder(cnpj(1), cnpj(2), cnpj(3), cnpj(4));
            } finally {
                claim.shutdownNow();
                soltar.countDown();
            }
            segurando.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(10)
    void reivindicacaoConcorrenteDeLeaseVencidoTemUmVencedor() throws Exception {
        insere(1, INICIO.minusSeconds(1));

        var respostas = concorrer(8, 5, 1);

        var donos = respostas.stream().flatMap(List::stream).map(ColetaReivindicada::dono).toList();
        assertThat(donos).hasSize(1);
        String donoGravado = jdbi.withHandle(h -> h.createQuery("select lease_owner from estado_coleta")
                .mapTo(String.class).one());
        assertThat(donoGravado).isEqualTo(donos.get(0));
    }
}
