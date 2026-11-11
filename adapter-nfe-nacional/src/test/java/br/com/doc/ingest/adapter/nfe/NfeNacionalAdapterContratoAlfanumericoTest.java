package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.domain.model.Cnpj;

/** Mesma suíte de contrato, com CNPJ consultado alfanumérico (NT Conjunta 2025.001). */
class NfeNacionalAdapterContratoAlfanumericoTest extends NfeNacionalAdapterContratoTest {

    @Override
    protected Cnpj cnpj() {
        return new Cnpj("12ABC34501DE35");
    }
}
