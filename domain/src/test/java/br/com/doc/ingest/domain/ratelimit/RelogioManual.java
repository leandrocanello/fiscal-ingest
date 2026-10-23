package br.com.doc.ingest.domain.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio de teste que só anda quando mandado. */
final class RelogioManual extends Clock {

    private Instant agora;

    RelogioManual(Instant inicio) {
        this.agora = inicio;
    }

    void avancar(Duration d) {
        agora = agora.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
