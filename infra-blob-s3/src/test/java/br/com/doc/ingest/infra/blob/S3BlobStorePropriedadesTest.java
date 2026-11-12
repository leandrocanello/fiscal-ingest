package br.com.doc.ingest.infra.blob;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Equivalência do {@link S3BlobStore} com o modelo em memória (invariantes 5 e 7; ADR-026). */
class S3BlobStorePropriedadesTest extends SeaweedFsTestBase {

    private sealed interface Operacao {}

    private record Gravar(String chave, byte[] conteudo, boolean hashErrado) implements Operacao {}

    private record Ler(String chave, byte[] conteudo) implements Operacao {}

    @Provide
    Arbitrary<List<Operacao>> operacoes() {
        Arbitrary<String> chaves = Arbitraries.of("a", "b", "c").map(s -> "raw/pull/f/producao/1/2026/11/12/" + s);
        Arbitrary<byte[]> conteudos = Arbitraries.of("", "x", "y", "zz")
                .map(s -> s.getBytes(StandardCharsets.UTF_8));
        Arbitrary<Operacao> gravar = Combinators.combine(chaves, conteudos, Arbitraries.of(false, false, false, true))
                .as(Gravar::new);
        Arbitrary<Operacao> ler = Combinators.combine(chaves, conteudos).as(Ler::new);
        return Arbitraries.oneOf(gravar, ler).list().ofMaxSize(12);
    }

    private static String executar(BlobStore store, Operacao op) {
        try {
            return switch (op) {
                case Gravar g -> {
                    String sha = g.hashErrado() ? Sha256.hex(new byte[] {42}) : Sha256.hex(g.conteudo());
                    BlobRef ref = store.gravar(g.chave(), g.conteudo(), sha);
                    yield "ref:" + ref.chave() + ":" + ref.sha256();
                }
                case Ler l -> store.ler(new BlobRef(l.chave(), Sha256.hex(l.conteudo())))
                        .map(b -> "bytes:" + HexFormat.of().formatHex(b))
                        .orElse("vazio");
            };
        } catch (BlobConflitanteException e) {
            return "conflito";
        } catch (IllegalArgumentException e) {
            return "argumento-invalido";
        }
    }

    @Property(tries = 50)
    void mesmosResultadosDoModeloEmMemoria(@ForAll("operacoes") List<Operacao> sequencia) {
        BlobStore modelo = new BlobStoreEmMemoria();
        try (S3Client s3 = novoCliente()) {
            BlobStore real = new S3BlobStore(s3, novoBucket(s3));
            List<String> esperado = new ArrayList<>();
            List<String> obtido = new ArrayList<>();
            for (Operacao op : sequencia) {
                esperado.add(executar(modelo, op));
                obtido.add(executar(real, op));
            }
            assertThat(obtido).isEqualTo(esperado);

            // no fim, cada chave guarda o conteúdo da primeira gravação válida (mesma leitura do modelo)
            for (String chave : List.of("a", "b", "c")) {
                for (String c : List.of("", "x", "y", "zz")) {
                    Ler l = new Ler("raw/pull/f/producao/1/2026/11/12/" + chave, c.getBytes(StandardCharsets.UTF_8));
                    assertThat(executar(real, l)).isEqualTo(executar(modelo, l));
                }
            }
        }
    }
}
