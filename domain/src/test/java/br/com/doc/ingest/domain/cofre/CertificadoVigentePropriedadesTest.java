package br.com.doc.ingest.domain.cofre;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CertificadoVigentePropriedadesTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    @Provide
    Arbitrary<List<CertificadoGuardado>> listas() {
        Arbitrary<Long> offsets = Arbitraries.longs().between(-20, 20);
        Arbitrary<Long> duracoes = Arbitraries.longs().between(0, 20);
        Arbitrary<Long> envios = Arbitraries.longs().between(0, 3);
        return Arbitraries.integers().between(0, 8).flatMap(n ->
                Arbitraries.longs().between(0, 3).list().ofSize(n).flatMap(ignorado ->
                        offsets.list().ofSize(n).flatMap(des ->
                                duracoes.list().ofSize(n).flatMap(durs ->
                                        envios.list().ofSize(n).map(envs -> {
                                            List<CertificadoGuardado> lista = new ArrayList<>();
                                            for (int i = 0; i < n; i++) {
                                                Instant de = AGORA.plusSeconds(des.get(i));
                                                lista.add(CertificadoVigenteTest.cert(i + 1L, de,
                                                        de.plusSeconds(durs.get(i)), AGORA.minusSeconds(100 - envs.get(i))));
                                            }
                                            return lista;
                                        })))));
    }

    private static boolean valido(CertificadoGuardado c) {
        return !AGORA.isBefore(c.validoDe()) && !AGORA.isAfter(c.validoAte());
    }

    @Property
    void vigenteSempreValidoNoInstante(@ForAll("listas") List<CertificadoGuardado> lista) {
        if (CertificadoVigente.escolher(lista, AGORA) instanceof SituacaoTitular.Vigente v) {
            assertThat(valido(v.certificado())).isTrue();
        }
    }

    @Property
    void nenhumOutroValidoTemValidoAteMaior(@ForAll("listas") List<CertificadoGuardado> lista) {
        var situacao = CertificadoVigente.escolher(lista, AGORA);
        if (situacao instanceof SituacaoTitular.Vigente v) {
            assertThat(lista.stream().filter(CertificadoVigentePropriedadesTest::valido))
                    .allMatch(o -> !o.validoAte().isAfter(v.certificado().validoAte()));
        } else {
            assertThat(lista).noneMatch(CertificadoVigentePropriedadesTest::valido);
        }
    }

    @Property
    void resultadoNaoDependeDaOrdemDaLista(@ForAll("listas") List<CertificadoGuardado> lista, @ForAll long semente) {
        List<CertificadoGuardado> embaralhada = new ArrayList<>(lista);
        Collections.shuffle(embaralhada, new Random(semente));
        assertThat(CertificadoVigente.escolher(embaralhada, AGORA)).isEqualTo(CertificadoVigente.escolher(lista, AGORA));
    }
}
