package br.com.doc.ingest.adapter.nfe.smoke;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Smoke manual em homologação (E2-S12). Fora da task {@code test}; nunca roda no CI (inv. 14). */
@Tag("homologacao")
@EnabledIfSystemProperty(named = "smoke.homologacao", matches = "true")
class SmokeHomologacaoTest {

    @Test
    void guarda() {
        fail("guarda");
    }
}
