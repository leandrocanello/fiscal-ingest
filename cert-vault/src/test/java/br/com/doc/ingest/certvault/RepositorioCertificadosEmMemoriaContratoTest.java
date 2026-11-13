package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.RepositorioCertificados;
import org.junit.jupiter.api.DisplayName;

@DisplayName("RepositorioCertificadosEmMemoria cumpre o contrato de RepositorioCertificados")
class RepositorioCertificadosEmMemoriaContratoTest extends RepositorioCertificadosContratoTest {

    @Override
    protected RepositorioCertificados novoRepositorio() {
        return new RepositorioCertificadosEmMemoria();
    }
}
