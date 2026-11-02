package br.com.doc.ingest.domain.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.catalogo.fixture.ArquivoComBytes;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class RegraSemBytesNoCatalogoTest {

    private static final ArchRule SEM_BYTES_NO_CATALOGO = noFields()
            .that().areDeclaredInClassesThat().resideInAPackage("br.com.doc.ingest.domain.catalogo..")
            .should().haveRawType(byte[].class)
            .because("o XML não passa pelo domínio: o catálogo só tem metadado e BlobRef (CLAUDE.md §2.2, inv. 3)");

    @Test
    void nenhumaClasseDoCatalogoTemCampoDeBytes() {
        JavaClasses dominio = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.doc.ingest.domain");

        SEM_BYTES_NO_CATALOGO.check(dominio);
    }

    @Test
    void metaTeste_regraFalhaEmFixtureComCampoDeBytes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ArquivoComBytes.class);

        assertThatThrownBy(() -> SEM_BYTES_NO_CATALOGO.check(fixture)).isInstanceOf(AssertionError.class);
    }
}
