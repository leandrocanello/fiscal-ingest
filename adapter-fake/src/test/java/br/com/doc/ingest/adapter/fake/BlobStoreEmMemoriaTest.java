package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
}
