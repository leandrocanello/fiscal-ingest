package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlobStoreEmMemoriaTest {

    private static final String CHAVE = "raw/pull/t1/12345678000199/2026/10/23/x.envelope.xml.gz";

    private final BlobStoreEmMemoria store = new BlobStoreEmMemoria();

    @Test
    void gravarDepoisLerDevolveMesmosBytes() {
        byte[] conteudo = "conteudo".getBytes(StandardCharsets.UTF_8);
        String sha = Sha256.hex(conteudo);

        BlobRef ref = store.gravar(CHAVE, conteudo, sha);

        assertThat(ref).isEqualTo(new BlobRef(CHAVE, sha));
        assertThat(store.ler(ref)).hasValue(conteudo);
    }

    @Test
    void lerChaveInexistenteDevolveVazio() {
        BlobRef ref = new BlobRef(CHAVE, Sha256.hex(new byte[0]));

        assertThat(store.ler(ref)).isEmpty();
    }

    @Test
    void lerComShaDiferenteDoGravadoDevolveVazio() {
        byte[] conteudo = "a".getBytes(StandardCharsets.UTF_8);
        store.gravar(CHAVE, conteudo, Sha256.hex(conteudo));

        BlobRef outra = new BlobRef(CHAVE, Sha256.hex("b".getBytes(StandardCharsets.UTF_8)));

        assertThat(store.ler(outra)).isEmpty();
    }

    @Test
    void gravarDuasVezesMesmoConteudoEhIdempotente() {
        byte[] conteudo = "conteudo".getBytes(StandardCharsets.UTF_8);
        String sha = Sha256.hex(conteudo);

        BlobRef primeira = store.gravar(CHAVE, conteudo, sha);
        BlobRef segunda = store.gravar(CHAVE, conteudo.clone(), sha);

        assertThat(segunda).isEqualTo(primeira);
        assertThat(store.ler(primeira)).hasValue(conteudo);
    }

    @Test
    void mesmaChaveConteudoDiferenteRejeitada() {
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] outro = "outro".getBytes(StandardCharsets.UTF_8);
        String shaOriginal = Sha256.hex(original);
        String shaOutro = Sha256.hex(outro);
        store.gravar(CHAVE, original, shaOriginal);

        assertThatThrownBy(() -> store.gravar(CHAVE, outro, shaOutro))
                .isInstanceOf(BlobConflitanteException.class)
                .hasMessageContaining(CHAVE)
                .hasMessageContaining(shaOriginal)
                .hasMessageContaining(shaOutro)
                .hasMessageNotContaining("original")
                .hasMessageNotContaining("outro");
        assertThat(store.ler(new BlobRef(CHAVE, shaOriginal))).hasValue(original);
    }
}
