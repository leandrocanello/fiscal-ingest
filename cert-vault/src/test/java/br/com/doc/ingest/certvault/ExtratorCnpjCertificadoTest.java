package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CertificadoTitular;
import br.com.doc.ingest.certvault.GeradorCertificadoDeTeste.CodificacaoCnpj;
import br.com.doc.ingest.domain.model.Cnpj;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExtratorCnpjCertificado (entrada não confiável)")
class ExtratorCnpjCertificadoTest {

    private static final String CNPJ = "11222333000181";
    private static final String OTHER_NAME_VALIDO = "3019" + "0605604c010303" + "a010" + "040e"
            + HexFormat.of().formatHex(CNPJ.getBytes(StandardCharsets.US_ASCII));

    private final GeradorCertificadoDeTeste gerador = new GeradorCertificadoDeTeste(
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));

    private static byte[] der(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    @DisplayName("extrai o CNPJ em OCTET STRING")
    void extraiDeOctetString() {
        CertificadoTitular titular = gerador.titular(CNPJ, CodificacaoCnpj.OCTET_STRING);

        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).contains(new Cnpj(CNPJ));
    }

    @Test
    @DisplayName("extrai o CNPJ em PrintableString")
    void extraiDePrintableString() {
        CertificadoTitular titular = gerador.titular(CNPJ, CodificacaoCnpj.PRINTABLE_STRING);

        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).contains(new Cnpj(CNPJ));
    }

    @Test
    @DisplayName("extrai o CNPJ em UTF8String")
    void extraiDeUtf8String() {
        CertificadoTitular titular = gerador.titular(CNPJ, CodificacaoCnpj.UTF8_STRING);

        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).contains(new Cnpj(CNPJ));
    }

    @Test
    @DisplayName("certificado sem SAN não tem CNPJ")
    void semSanDevolveVazio() {
        CertificadoTitular titular = gerador.semSan(CNPJ);

        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).isEmpty();
    }

    @Test
    @DisplayName("e-CPF traz outro OID e não tem CNPJ")
    void eCpfDevolveVazio() {
        CertificadoTitular titular = gerador.eCpf("12345678909");

        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).isEmpty();
    }

    @Test
    @DisplayName("CNPJ no CN, sem o OID, é ignorado: o CN é texto livre")
    void cnIgnorado() {
        CertificadoTitular titular = gerador.eCpf(CNPJ);

        assertThat(titular.certificado().getSubjectX500Principal().getName()).contains(CNPJ);
        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1122233300018", "112223330001811", "1122233300018A", "11222333 00181"})
    @DisplayName("valor no OID certo que não são 14 dígitos não vira CNPJ")
    void valorQueNaoTem14DigitosDevolveVazio(String valor) {
        CertificadoTitular titular = gerador.titular(valor, CodificacaoCnpj.PRINTABLE_STRING);

        assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11222333000182", "11222333000191"})
    @DisplayName("CNPJ com DV errado no certificado devolve vazio, sem lançar")
    void dvErradoDevolveVazio(String valor) {
        for (CodificacaoCnpj codificacao : CodificacaoCnpj.values()) {
            CertificadoTitular titular = gerador.titular(valor, codificacao);

            assertThat(ExtratorCnpjCertificado.extrair(titular.certificado())).as("%s", codificacao).isEmpty();
        }
    }

    @Test
    @DisplayName("o OtherName feito à mão confirma a leitura; todo prefixo dele é recusado")
    void derTruncadoDevolveVazio() {
        byte[] valido = der(OTHER_NAME_VALIDO);

        assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(valido)).contains(new Cnpj(CNPJ));
        for (int tamanho = 0; tamanho < valido.length; tamanho++) {
            assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(Arrays.copyOf(valido, tamanho)))
                    .as("prefixo de %d bytes", tamanho)
                    .isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "30ff0605604c010303",                  // comprimento além do fim do array
            "3080" + "0605604c010303",             // comprimento indefinido não existe em DER
            "3084ffffffff" + "0605604c010303",     // comprimento que estouraria int
            "3082ffff" + "0605604c010303",         // forma longa além do fim do array
            "3007" + "06ff604c010303",             // comprimento interno além da sequência
            "3007" + "0605604c010303",             // sem o valor [0]
            "3009" + "0605604c010303" + "a000",    // [0] vazio
            "300b" + "0605604c010303" + "a0020400" // valor vazio
    })
    @DisplayName("comprimentos mentirosos ou estrutura incompleta são recusados")
    void estruturaInvalidaDevolveVazio(String hex) {
        assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(der(hex))).isEmpty();
    }

    @Test
    @DisplayName("bytes sobrando depois do OtherName tornam a estrutura inválida")
    void bytesSobrandoDevolveVazio() {
        assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(der(OTHER_NAME_VALIDO + "00"))).isEmpty();
    }

    @Test
    @DisplayName("outro tipo de string no valor não é aceito")
    void tipoDeValorDesconhecidoDevolveVazio() {
        String ia5 = OTHER_NAME_VALIDO.replace("a010040e", "a010160e");

        assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(der(ia5))).isEmpty();
    }

    @Property
    void bytesAleatoriosNuncaLancam(@ForAll @Size(max = 256) byte[] bytes) {
        assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(bytes)).isNotNull();
    }

    @Property
    void derValidoAlteradoNuncaLanca(@ForAll @IntRange(min = 0, max = 26) int posicao,
                                     @ForAll byte valor) {
        byte[] alterado = der(OTHER_NAME_VALIDO);
        alterado[posicao] = valor;

        assertThat(ExtratorCnpjCertificado.cnpjDoOtherName(alterado)).isNotNull();
    }
}
