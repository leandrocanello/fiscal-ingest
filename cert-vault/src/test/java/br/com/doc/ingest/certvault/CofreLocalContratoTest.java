package br.com.doc.ingest.certvault;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.domain.cofre.CofreDeCertificados;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Set;

@DisplayName("CofreLocal cumpre o contrato de CofreDeCertificados")
class CofreLocalContratoTest extends CofreDeCertificadosContratoTest {

    @Override
    protected CofreDeCertificados novoCofre() {
        return new CofreLocal(
                new ValidadorPfx(new ConfiancaIcp(Set.of(gerador.raiz()), List.of(gerador.intermediaria())),
                        relogio, ValidadorPfx.LIMITE_PADRAO_BYTES),
                new EnvelopeCifrado(), new KekLocal("kek-1", Map.of("kek-1", new byte[32])),
                new BlobStoreEmMemoria(), new RepositorioCertificadosEmMemoria(), relogio);
    }
}
