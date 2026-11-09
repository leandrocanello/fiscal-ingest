package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CertificadoGuardado e NovoCertificado (só cifrado + metadados)")
class CertificadoGuardadoTest {

    private static final Cnpj CNPJ = new Cnpj("12345678000199");
    private static final String FINGERPRINT = "f".repeat(64);
    private static final BlobRef PFX_CIFRADO = new BlobRef("cofre/1/ff/pfx-aaa.env", "a".repeat(64));
    private static final BlobRef SENHA_CIFRADA = new BlobRef("cofre/1/ff/senha-bbb.env", "b".repeat(64));
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant ENVIADO = Instant.parse("2026-11-03T12:00:00Z");

    private static CertificadoGuardado guardado(Instant de, Instant ate) {
        return new CertificadoGuardado(10L, 1L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO, SENHA_CIFRADA,
                de, ate, ENVIADO, "operador");
    }

    private static NovoCertificado novo(Instant de, Instant ate) {
        return new NovoCertificado(1L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO, SENHA_CIFRADA,
                de, ate, ENVIADO, "operador");
    }

    @Test
    @DisplayName("rejeita validoAte antes de validoDe; datas iguais são aceitas")
    void rejeitaValidoAteAntesDeValidoDe() {
        assertThatThrownBy(() -> guardado(ATE, DE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> novo(ATE, DE)).isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> guardado(DE, DE)).doesNotThrowAnyException();
        assertThatCode(() -> novo(DE, DE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejeita id, tenant ou titular não positivos, fingerprint malformado e nulos")
    void rejeitaCamposInvalidos() {
        assertThatThrownBy(() -> new CertificadoGuardado(0L, 1L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(0L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 0L, CNPJ, FINGERPRINT, PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 2L, CNPJ, "ABC", PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 2L, null, FINGERPRINT, PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 2L, CNPJ, FINGERPRINT, null,
                SENHA_CIFRADA, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO,
                null, DE, ATE, ENVIADO, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, null, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NovoCertificado(1L, 2L, CNPJ, FINGERPRINT, PFX_CIFRADO,
                SENHA_CIFRADA, DE, ATE, ENVIADO, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString não mostra as referências dos blobs cifrados")
    void toStringNaoMostraAsReferencias() {
        for (String texto : new String[] {guardado(DE, ATE).toString(), novo(DE, ATE).toString()}) {
            assertThat(texto).doesNotContain(PFX_CIFRADO.chave());
            assertThat(texto).doesNotContain(PFX_CIFRADO.sha256());
            assertThat(texto).doesNotContain(SENHA_CIFRADA.chave());
            assertThat(texto).doesNotContain(SENHA_CIFRADA.sha256());
        }
    }
}
