package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do {@link BlobStoreEmMemoria} (CLAUDE.md §2.2 invariante 7 — blob imutável; ADR-026).
 */
class BlobStoreEmMemoriaPropriedadesTest {

    private record Gravacao(String chave, byte[] conteudo) {}

    @Provide
    Arbitrary<List<Gravacao>> gravacoes() {
        Arbitrary<String> chaves = Arbitraries.of("a", "b", "c").map(s -> "raw/push/t1/" + s + ".bin");
        Arbitrary<byte[]> conteudos = Arbitraries.bytes().array(byte[].class).ofMaxSize(3);
        return net.jqwik.api.Combinators.combine(chaves, conteudos).as(Gravacao::new).list().ofMaxSize(40);
    }

    @Property
    void regravarNuncaAlteraConteudo(@ForAll("gravacoes") List<Gravacao> sequencia) {
        BlobStoreEmMemoria store = new BlobStoreEmMemoria();
        Map<String, byte[]> primeiros = new HashMap<>();

        for (Gravacao g : sequencia) {
            String sha = Sha256.hex(g.conteudo());
            byte[] primeiro = primeiros.get(g.chave());
            if (primeiro == null) {
                store.gravar(g.chave(), g.conteudo(), sha);
                primeiros.put(g.chave(), g.conteudo());
            } else if (Sha256.hex(primeiro).equals(sha)) {
                store.gravar(g.chave(), g.conteudo(), sha);
            } else {
                try {
                    store.gravar(g.chave(), g.conteudo(), sha);
                    throw new AssertionError("conflito deveria ser rejeitado");
                } catch (BlobConflitanteException esperado) {
                    // ok
                }
            }
            // após qualquer gravação, o conteúdo lido é sempre o primeiro
            byte[] esperado = primeiros.get(g.chave());
            assertThat(store.ler(new BlobRef(g.chave(), Sha256.hex(esperado)))).hasValue(esperado);
        }
    }

    @Property(tries = 20)
    void gravacoesConcorrentesDoMesmoParResultamNoMesmoBlobRef(@ForAll("conteudo") byte[] conteudo) throws Exception {
        BlobStoreEmMemoria store = new BlobStoreEmMemoria();
        String chave = "raw/push/t1/concorrente.bin";
        String sha = Sha256.hex(conteudo);

        List<Future<BlobRef>> futuros = new ArrayList<>();
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 32; i++) {
                futuros.add(exec.submit(() -> store.gravar(chave, conteudo, sha)));
            }
        }

        for (Future<BlobRef> f : futuros) {
            assertThat(f.get()).isEqualTo(new BlobRef(chave, sha));
        }
        assertThat(store.ler(new BlobRef(chave, sha))).hasValue(conteudo);
    }

    @Provide
    Arbitrary<byte[]> conteudo() {
        return Arbitraries.bytes().array(byte[].class).ofMaxSize(64);
    }
}
