package br.com.doc.ingest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.blob.BlobRef;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentoTest {

    private static final String SHA = "a".repeat(64);

    @Test
    void documentoLevaBlobRefComChaveESha() {
        Documento d = new Documento(UUID.randomUUID(), new FonteId("fake"), new Cnpj("11222333000181"),
                "55", new Nsu(1), Instant.EPOCH, "resNFe", Optional.empty(),
                Optional.of(new BlobRef("x/1.xml", SHA)));

        assertThat(d.blobRef().map(BlobRef::sha256)).contains(SHA);
        assertThat(d.blobRef().map(BlobRef::chave)).contains("x/1.xml");
    }
}
