package br.com.doc.ingest.domain.arquitetura;

import br.com.doc.ingest.domain.arquitetura.fixture.DependeDeBiblioteca;
import br.com.doc.ingest.domain.arquitetura.fixture.LeArquivo;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArquiteturaDominioTest {

    private static final JavaClasses DOMINIO = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("br.com.doc.ingest.domain");

    @Test
    void dominioSoDependeDoJdkEDeSiMesmo() {
        RegrasDoDominio.SO_DEPENDE_DO_JDK_E_DE_SI_MESMO.check(DOMINIO);
    }

    @Test
    void metaTeste_regraDeDependenciasFalhaEmFixtureQueUsaBiblioteca() {
        JavaClasses fixture = new ClassFileImporter().importClasses(DependeDeBiblioteca.class);
        assertThatThrownBy(() -> RegrasDoDominio.SO_DEPENDE_DO_JDK_E_DE_SI_MESMO.check(fixture))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void dominioNaoFazIo() {
        RegrasDoDominio.NAO_FAZ_IO.check(DOMINIO);
    }

    @Test
    void metaTeste_regraDeIoFalhaEmFixtureQueLeArquivo() {
        JavaClasses fixture = new ClassFileImporter().importClasses(LeArquivo.class);
        assertThatThrownBy(() -> RegrasDoDominio.NAO_FAZ_IO.check(fixture))
                .isInstanceOf(AssertionError.class);
    }
}
