package br.com.doc.ingest.certvault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KekLocal (chaves que embrulham as DEKs, só em memória)")
class KekLocalTest {

    private static byte[] chave(int preenchimento) {
        byte[] chave = new byte[32];
        Arrays.fill(chave, (byte) preenchimento);
        return chave;
    }

    @Test
    @DisplayName("a KEK exige id preenchido e chave de exatamente 32 bytes")
    void exige32Bytes() {
        assertThatThrownBy(() -> new Kek("k1", new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Kek("k1", new byte[33]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Kek("k1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Kek(" ", chave(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Kek(null, chave(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("faz cópia defensiva da chave na entrada e no acessor")
    void copiaDefensivaDaChave() {
        byte[] original = chave(1);
        Kek kek = new Kek("k1", original);

        original[0] = 99;
        kek.chave()[1] = 99;

        assertThat(kek.chave()).isEqualTo(chave(1));
    }

    @Test
    @DisplayName("alterar o mapa recebido depois não afeta o provedor")
    void copiaDefensivaDoMapa() {
        byte[] original = chave(1);
        Map<String, byte[]> chaves = new HashMap<>(Map.of("k1", original));
        KekLocal provedor = new KekLocal("k1", chaves);

        original[0] = 99;
        chaves.clear();

        assertThat(provedor.atual().chave()).isEqualTo(chave(1));
        assertThat(provedor.porId("k1")).isPresent();
    }

    @Test
    @DisplayName("toString mostra o id e não mostra a chave")
    void toStringNaoMostraAChave() {
        byte[] bytes = chave(0xAB);

        assertThat(new Kek("k1", bytes).toString())
                .contains("k1")
                .doesNotContain(HexFormat.of().formatHex(bytes))
                .doesNotContain(Arrays.toString(bytes))
                .doesNotContain(bytes.getClass().getName());
    }

    @Test
    @DisplayName("resolve a KEK atual e também as antigas pelo id")
    void resolveAKekAtualEAsAntigasPeloId() {
        KekLocal provedor = new KekLocal("k2", Map.of("k1", chave(1), "k2", chave(2)));

        assertThat(provedor.atual().id()).isEqualTo("k2");
        assertThat(provedor.atual().chave()).isEqualTo(chave(2));
        assertThat(provedor.porId("k1")).hasValueSatisfying(kek -> {
            assertThat(kek.id()).isEqualTo("k1");
            assertThat(kek.chave()).isEqualTo(chave(1));
        });
    }

    @Test
    @DisplayName("id desconhecido devolve vazio")
    void idDesconhecidoDevolveVazio() {
        KekLocal provedor = new KekLocal("k1", Map.of("k1", chave(1)));

        assertThat(provedor.porId("k9")).isEmpty();
    }

    @Test
    @DisplayName("a KEK atual precisa estar no mapa")
    void kekAtualPrecisaEstarNoMapa() {
        assertThatThrownBy(() -> new KekLocal("k9", Map.of("k1", chave(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KekLocal("k1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KekLocal("k1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chave de tamanho errado no mapa é recusada sem mostrar o conteúdo")
    void chaveDeTamanhoErradoNaoApareceNaMensagem() {
        byte[] curta = new byte[16];
        Arrays.fill(curta, (byte) 0xAB);

        assertThatThrownBy(() -> new KekLocal("k1", Map.of("k1", curta)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(HexFormat.of().formatHex(curta))
                .hasMessageNotContaining(Arrays.toString(curta));
    }
}
