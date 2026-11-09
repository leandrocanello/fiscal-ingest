package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.FalhaAbertura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnvelopeCifrado (AES-256-GCM com DEK embrulhada pela KEK)")
class EnvelopeCifradoTest {

    private static final byte[] AAD = "1|2|abc|PFX".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEUDO = "conteudo sintetico do pfx".getBytes(StandardCharsets.UTF_8);

    private final EnvelopeCifrado envelope = new EnvelopeCifrado();
    private final KekLocal keks = new KekLocal("k1", Map.of("k1", chave(1)));

    private static byte[] chave(int preenchimento) {
        byte[] chave = new byte[32];
        Arrays.fill(chave, (byte) preenchimento);
        return chave;
    }

    private byte[] cifrar(byte[] conteudo) {
        return envelope.cifrar(conteudo, envelope.novaDek(), keks.atual(), AAD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "x", "senha não ASCII ç€"})
    @DisplayName("cifra e decifra de volta, inclusive conteúdo vazio (senha vazia é válida)")
    void cifraEDecifraDeVolta(String texto) {
        byte[] conteudo = texto.getBytes(StandardCharsets.UTF_8);

        ResultadoDecifra resultado = envelope.decifrar(cifrar(conteudo), AAD, keks);

        assertThat(resultado).isInstanceOf(ResultadoDecifra.Decifrado.class);
        assertThat(((ResultadoDecifra.Decifrado) resultado).conteudo()).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("o mesmo conteúdo com a mesma DEK gera cifrados diferentes (nonce aleatório)")
    void mesmoConteudoGeraCifradosDiferentes() {
        byte[] dek = envelope.novaDek();

        byte[] primeiro = envelope.cifrar(CONTEUDO, dek, keks.atual(), AAD);
        byte[] segundo = envelope.cifrar(CONTEUDO, dek, keks.atual(), AAD);

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("cada geração produz uma DEK de 256 bits diferente")
    void dekEhDiferenteACadaGeracao() {
        byte[] primeira = envelope.novaDek();
        byte[] segunda = envelope.novaDek();

        assertThat(primeira).hasSize(32);
        assertThat(segunda).hasSize(32);
        assertThat(primeira).isNotEqualTo(segunda);
    }

    @Test
    @DisplayName("AAD diferente não decifra (trocar blobs entre certificados ou partes)")
    void aadDiferenteNaoDecifra() {
        byte[] outraAad = "1|2|abc|SENHA".getBytes(StandardCharsets.UTF_8);

        ResultadoDecifra resultado = envelope.decifrar(cifrar(CONTEUDO), outraAad, keks);

        assertThat(resultado).isEqualTo(new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO));
    }

    @Test
    @DisplayName("KEK errada com o mesmo id não decifra")
    void kekErradaNaoDecifra() {
        KekLocal outra = new KekLocal("k1", Map.of("k1", chave(2)));

        ResultadoDecifra resultado = envelope.decifrar(cifrar(CONTEUDO), AAD, outra);

        assertThat(resultado).isEqualTo(new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 255})
    @DisplayName("versão desconhecida do formato não decifra")
    void versaoDesconhecidaNaoDecifra(int versao) {
        byte[] cifrado = cifrar(CONTEUDO);
        cifrado[0] = (byte) versao;

        ResultadoDecifra resultado = envelope.decifrar(cifrado, AAD, keks);

        assertThat(resultado).isEqualTo(new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO));
    }

    @Test
    @DisplayName("envelope truncado em qualquer ponto não decifra e não lança")
    void envelopeTruncadoNaoDecifra() {
        byte[] cifrado = cifrar(CONTEUDO);

        for (int tamanho = 0; tamanho < cifrado.length; tamanho++) {
            ResultadoDecifra resultado = envelope.decifrar(Arrays.copyOf(cifrado, tamanho), AAD, keks);

            assertThat(resultado)
                    .as("truncado em %d bytes", tamanho)
                    .isEqualTo(new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO));
        }
    }

    @Test
    @DisplayName("depois de uma KEK nova, a antiga ainda decifra o que cifrou")
    void kekAntigaAindaDecifra() {
        byte[] cifrado = cifrar(CONTEUDO);
        KekLocal rotacionada = new KekLocal("k2", Map.of("k1", chave(1), "k2", chave(2)));

        ResultadoDecifra resultado = envelope.decifrar(cifrado, AAD, rotacionada);

        assertThat(resultado).isInstanceOf(ResultadoDecifra.Decifrado.class);
        assertThat(((ResultadoDecifra.Decifrado) resultado).conteudo()).isEqualTo(CONTEUDO);
    }

    @Test
    @DisplayName("KEK desconhecida é distinguida de envelope inválido")
    void kekDesconhecidaEhDistinguida() {
        KekLocal semAKek = new KekLocal("k2", Map.of("k2", chave(2)));

        ResultadoDecifra resultado = envelope.decifrar(cifrar(CONTEUDO), AAD, semAKek);

        assertThat(resultado).isEqualTo(new ResultadoDecifra.Falha(FalhaAbertura.KEK_DESCONHECIDA));
    }

    @Test
    @DisplayName("o toString do resultado não mostra o conteúdo decifrado")
    void toStringNaoMostraOConteudo() {
        ResultadoDecifra resultado = new ResultadoDecifra.Decifrado(new byte[] {65, 66, 67});

        assertThat(resultado.toString()).doesNotContain("65").doesNotContain("ABC").contains("***");
    }
}
