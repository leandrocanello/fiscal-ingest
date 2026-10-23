package br.com.doc.ingest.domain.blob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BlobRef (value object)")
class BlobRefTest {

    private static final String SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    @DisplayName("aceita chave do layout de armazenamento e sha256 válido")
    void aceitaChaveValida() {
        var chave = "raw/pull/t1/12345678000199/2026/10/23/x.envelope.xml.gz";
        var ref = new BlobRef(chave, SHA);
        assertThat(ref.chave()).isEqualTo(chave);
        assertThat(ref.sha256()).isEqualTo(SHA);
    }

    @Test
    @DisplayName("rejeita chave nula ou em branco")
    void rejeitaChaveNulaOuBranca() {
        assertThatThrownBy(() -> new BlobRef(null, SHA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlobRef("  ", SHA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita chave com '..' ou começando por '/'")
    void rejeitaChavePerigosa() {
        assertThatThrownBy(() -> new BlobRef("raw/../x", SHA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlobRef("/raw/x", SHA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita sha256 que não seja 64 hex minúsculos")
    void rejeitaShaInvalido() {
        assertThatThrownBy(() -> new BlobRef("a/b", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlobRef("a/b", SHA.substring(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlobRef("a/b", SHA.toUpperCase())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlobRef("a/b", "g" + SHA.substring(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
