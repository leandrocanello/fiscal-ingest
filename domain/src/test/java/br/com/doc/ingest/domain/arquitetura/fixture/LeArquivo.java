package br.com.doc.ingest.domain.arquitetura.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fixture violadora: o domínio não pode fazer I/O. */
public class LeArquivo {
    public byte[] le(Path caminho) throws IOException {
        return Files.readAllBytes(caminho);
    }
}
