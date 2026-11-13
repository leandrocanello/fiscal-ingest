package br.com.doc.ingest.adapter.nfe.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

class CursorLocalPropriedadesTest {

    @Property(tries = 50)
    void valorLidoNuncaDecrescEEhOMaximoAceito(
            @ForAll @Size(max = 30) List<@LongRange(min = 0, max = 1_000) Long> sequencia) throws Exception {
        Path dir = Files.createTempDirectory("cursor-prop");
        var cursor = new CursorLocal(dir, new Cnpj("11222333000181"));
        long maximo = 0;
        for (long n : sequencia) {
            long anterior = cursor.ler().valor();
            try {
                cursor.gravar(new Nsu(n));
                maximo = Math.max(maximo, n);
            } catch (IllegalStateException esperado) {
                assertThat(n).isLessThan(maximo);
            }
            assertThat(cursor.ler().valor()).isGreaterThanOrEqualTo(anterior).isEqualTo(maximo);
        }
    }
}
