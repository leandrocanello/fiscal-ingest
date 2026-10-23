package br.com.doc.ingest.domain.arquitetura;

import br.com.doc.ingest.domain.arquitetura.fixture.CStatVazado;
import br.com.doc.ingest.domain.arquitetura.fixture.DependeDeBiblioteca;
import br.com.doc.ingest.domain.arquitetura.fixture.LeArquivo;
import br.com.doc.ingest.domain.arquitetura.fixture.UsaRelogioDoSistema;
import br.com.doc.ingest.domain.arquitetura.fixture.UsaRelogioInjetado;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void dominioNaoUsaRelogioSolto() {
        RegrasDoDominio.NAO_USA_RELOGIO_SOLTO.check(DOMINIO);
    }

    @Test
    void metaTeste_regraDeRelogioFalhaEmFixtureComInstantNowSemArgumento() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UsaRelogioDoSistema.class);
        assertThatThrownBy(() -> RegrasDoDominio.NAO_USA_RELOGIO_SOLTO.check(fixture))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void metaTeste_regraDeRelogioPermiteInstantNowComClock() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UsaRelogioInjetado.class);
        assertThatCode(() -> RegrasDoDominio.NAO_USA_RELOGIO_SOLTO.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void nenhumaClasseDoDominioFalaDeCStat() {
        RegrasDoDominio.NAO_FALA_DE_CSTAT.check(DOMINIO);
    }

    @Test
    void metaTeste_regraDeCStatFalhaEmFixtureComMembroCStat() {
        JavaClasses fixture = new ClassFileImporter().importClasses(CStatVazado.class);
        assertThatThrownBy(() -> RegrasDoDominio.NAO_FALA_DE_CSTAT.check(fixture))
                .isInstanceOf(AssertionError.class);
    }
}
