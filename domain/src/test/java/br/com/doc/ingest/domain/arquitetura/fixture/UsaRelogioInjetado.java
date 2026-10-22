package br.com.doc.ingest.domain.arquitetura.fixture;

import java.time.Clock;
import java.time.Instant;

/** Fixture permitida: relógio injetado. */
public class UsaRelogioInjetado {
    public Instant agora(Clock clock) {
        return Instant.now(clock);
    }
}
