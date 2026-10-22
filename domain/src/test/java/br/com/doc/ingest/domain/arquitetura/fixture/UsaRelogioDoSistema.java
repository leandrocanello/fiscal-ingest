package br.com.doc.ingest.domain.arquitetura.fixture;

import java.time.Instant;

/** Fixture violadora: relógio solto no domínio. */
public class UsaRelogioDoSistema {
    public Instant agora() {
        return Instant.now();
    }
}
