package br.com.doc.ingest.domain.arquitetura.fixture;

import org.assertj.core.api.Assertions;

/** Fixture violadora: o domínio não pode depender de bibliotecas. */
public class DependeDeBiblioteca {
    public void usa() {
        Assertions.assertThat(1).isEqualTo(1);
    }
}
