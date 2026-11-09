package br.com.doc.ingest.certvault;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeCifradoPropriedadesTest {

    private static final byte[] AAD = "1|2|abc|PFX".getBytes(StandardCharsets.UTF_8);

    private final EnvelopeCifrado envelope = new EnvelopeCifrado();
    private final KekLocal keks = new KekLocal("k1", Map.of("k1", chave()));

    private static byte[] chave() {
        byte[] chave = new byte[32];
        Arrays.fill(chave, (byte) 7);
        return chave;
    }

    private static boolean contem(byte[] palheiro, byte[] agulha) {
        for (int i = 0; i <= palheiro.length - agulha.length; i++) {
            if (Arrays.equals(palheiro, i, i + agulha.length, agulha, 0, agulha.length)) {
                return true;
            }
        }
        return false;
    }

    @Property
    void trocarQualquerByteNaoDecifra(@ForAll @Size(max = 64) byte[] conteudo,
                                      @ForAll @IntRange(min = 0, max = 100_000) int posicao,
                                      @ForAll @IntRange(min = 1, max = 255) int mascara) {
        byte[] cifrado = envelope.cifrar(conteudo, envelope.novaDek(), keks.atual(), AAD);
        cifrado[posicao % cifrado.length] ^= (byte) mascara;

        assertThat(envelope.decifrar(cifrado, AAD, keks)).isInstanceOf(ResultadoDecifra.Falha.class);
    }

    @Property
    void idaEVoltaPreservaOConteudo(@ForAll @Size(max = 4096) byte[] conteudo,
                                    @ForAll @Size(max = 128) byte[] aad) {
        byte[] cifrado = envelope.cifrar(conteudo, envelope.novaDek(), keks.atual(), aad);

        ResultadoDecifra resultado = envelope.decifrar(cifrado, aad, keks);

        assertThat(resultado).isInstanceOf(ResultadoDecifra.Decifrado.class);
        assertThat(((ResultadoDecifra.Decifrado) resultado).conteudo()).isEqualTo(conteudo);
    }

    @Property
    void cifradoNuncaContemOConteudoClaro(@ForAll @Size(min = 16, max = 4096) byte[] conteudo) {
        byte[] dek = envelope.novaDek();

        byte[] cifrado = envelope.cifrar(conteudo, dek, keks.atual(), AAD);

        assertThat(contem(cifrado, conteudo)).isFalse();
        assertThat(contem(cifrado, dek)).isFalse();
        assertThat(contem(cifrado, keks.atual().chave())).isFalse();
    }
}
