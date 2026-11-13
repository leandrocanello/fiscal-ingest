package br.com.doc.ingest.adapter.nfe.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlobStoreEmDiscoSmokeTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void gravaEmPastaDoDiaELeDeVolta() throws Exception {
        var store = new BlobStoreEmDiscoSmoke(dir, RELOGIO);
        byte[] c = bytes("abc");
        BlobRef ref = store.gravar("raw/pull/x.envelope.xml", c, Sha256.hex(c));

        assertThat(Files.exists(dir.resolve("2026-11-13"))).isTrue();
        try (var arquivos = Files.list(dir.resolve("2026-11-13"))) {
            assertThat(arquivos).hasSize(1);
        }
        assertThat(store.ler(ref)).contains(c);
    }

    @Test
    void mesmaChaveEMesmoHashEhNoOp() {
        var store = new BlobStoreEmDiscoSmoke(dir, RELOGIO);
        byte[] c = bytes("abc");
        BlobRef a = store.gravar("k", c, Sha256.hex(c));
        BlobRef b = store.gravar("k", c, Sha256.hex(c));
        assertThat(b).isEqualTo(a);
    }

    @Test
    void mesmaChaveComConteudoDiferenteERecusada() {
        var store = new BlobStoreEmDiscoSmoke(dir, RELOGIO);
        byte[] a = bytes("a");
        byte[] b = bytes("b");
        store.gravar("k", a, Sha256.hex(a));
        assertThatThrownBy(() -> store.gravar("k", b, Sha256.hex(b)))
                .isInstanceOf(BlobConflitanteException.class);
        assertThat(store.ler(new BlobRef("k", Sha256.hex(a)))).contains(a);
    }

    @Test
    void sha256QueNaoBateComOsBytesERecusado() {
        var store = new BlobStoreEmDiscoSmoke(dir, RELOGIO);
        assertThatThrownBy(() -> store.gravar("k", bytes("a"), Sha256.hex(bytes("b"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.ler(new BlobRef("k", Sha256.hex(bytes("b"))))).isEmpty();
    }

    @Test
    void chaveComBarrasViraNomeDeArquivoSaneado() throws Exception {
        var store = new BlobStoreEmDiscoSmoke(dir, RELOGIO);
        byte[] c = bytes("abc");
        store.gravar("raw/pull/a/b.xml", c, Sha256.hex(c));
        try (var arquivos = Files.list(dir.resolve("2026-11-13"))) {
            assertThat(arquivos.map(p -> p.getFileName().toString())).noneMatch(n -> n.contains("/"));
        }
    }

    @Test
    void recusaPastaDentroDoRepositorio() throws Exception {
        Path repo = Files.createDirectories(dir.resolve("repo"));
        Files.createDirectory(repo.resolve(".git"));
        Path dentro = repo.resolve("sub/smoke");
        assertThatThrownBy(() -> new BlobStoreEmDiscoSmoke(dentro, RELOGIO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".git");
    }
}
