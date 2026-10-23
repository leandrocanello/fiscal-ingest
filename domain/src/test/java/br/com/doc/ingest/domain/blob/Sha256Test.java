package br.com.doc.ingest.domain.blob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sha256 (utilitário)")
class Sha256Test {

    @Test
    @DisplayName("hex de \"abc\" devolve o vetor oficial")
    void vetorOficialAbc() {
        assertThat(Sha256.hex("abc".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("hex de bytes vazios devolve o vetor oficial")
    void bytesVazios() {
        assertThat(Sha256.hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
