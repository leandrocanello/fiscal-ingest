package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.ledger.StatusNsu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Classificador de schema do docZip")
class ClassificadorSchemaTest {

    @ParameterizedTest
    @CsvSource({
            "resNFe_v1.01.xsd, RES_NFE",
            "procNFe_v4.00.xsd, PROC_NFE",
            "resEvento_v1.01.xsd, RES_EVENTO",
            "procEventoNFe_v1.00.xsd, PROC_EVENTO_NFE",
            "resNFe, RES_NFE",
            "procNFe, PROC_NFE",
            "resEvento, RES_EVENTO",
            "procEventoNFe, PROC_EVENTO_NFE",
            "procNFe.xsd, PROC_NFE"
    })
    @DisplayName("schemas conhecidos, com e sem sufixo de versão")
    void schemasConhecidos(String schema, TipoSchemaDfe esperado) {
        assertThat(ClassificadorSchema.classificar(schema)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"xpto_v9.xsd", "  ", "PROCNFE_v4.00.xsd"})
    @DisplayName("schema desconhecido, vazio ou nulo vira DESCONHECIDO")
    void schemasDesconhecidos(String schema) {
        assertThat(ClassificadorSchema.classificar(schema)).isEqualTo(TipoSchemaDfe.DESCONHECIDO);
    }

    @Test
    @DisplayName("status sugerido por tipo")
    void statusSugerido() {
        assertThat(TipoSchemaDfe.RES_NFE.statusSugerido()).contains(StatusNsu.RESUMO);
        assertThat(TipoSchemaDfe.PROC_NFE.statusSugerido()).contains(StatusNsu.BAIXADO);
        assertThat(TipoSchemaDfe.RES_EVENTO.statusSugerido()).contains(StatusNsu.EVENTO);
        assertThat(TipoSchemaDfe.PROC_EVENTO_NFE.statusSugerido()).contains(StatusNsu.EVENTO);
        assertThat(TipoSchemaDfe.DESCONHECIDO.statusSugerido()).isEqualTo(Optional.empty());
    }
}
