package br.com.doc.ingest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/** Garante que o jqwik (ADR-026) está disponível e executando junto com o JUnit. */
class JqwikDisponivelTest {

    @Property
    void somaEhComutativa(@ForAll long a, @ForAll long b) {
        assertThat(a + b).isEqualTo(b + a);
    }
}
