package br.com.doc.ingest.domain.arquitetura;

import br.com.doc.ingest.domain.arquitetura.fixture.ParserComBytes;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Inv. 3: os tipos de {@code domain.parser} só carregam metadado; o XML vem só como parâmetro. */
class RegraSemBytesNoParserTest {

    private static final ArchRule SEM_CAMPO_BYTE_ARRAY = noFields()
            .should().haveRawType(byte[].class)
            .because("o XML nunca fica guardado em tipo do domínio (CLAUDE.md §2.2, inv. 3)");

    @Test
    void nenhumTipoDoParserTemCampoByteArray() {
        JavaClasses parser = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.doc.ingest.domain.parser");
        SEM_CAMPO_BYTE_ARRAY.check(parser);
    }

    @Test
    void metaTeste_regraFalhaEmFixtureComCampoByteArray() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ParserComBytes.class);
        assertThatThrownBy(() -> SEM_CAMPO_BYTE_ARRAY.check(fixture)).isInstanceOf(AssertionError.class);
    }
}
