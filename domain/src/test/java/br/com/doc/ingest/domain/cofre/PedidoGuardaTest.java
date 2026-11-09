package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.model.Cnpj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PedidoGuarda (upload de pfx + senha, só em memória)")
class PedidoGuardaTest {

    private static final Cnpj CNPJ = new Cnpj("12345678000199");
    private static final byte[] PFX = {0x30, (byte) 0x82, 0x01, 0x02, 0x7f};
    private static final char[] SENHA = "segredo-sintetico".toCharArray();

    private static PedidoGuarda pedido(byte[] pfx, char[] senha) {
        return new PedidoGuarda(1L, 2L, CNPJ, pfx, senha, "operador");
    }

    @Test
    @DisplayName("faz cópia defensiva do pfx e da senha na entrada e nos acessores")
    void copiaDefensivaDoPfxEDaSenha() {
        byte[] pfx = PFX.clone();
        char[] senha = SENHA.clone();
        var pedido = pedido(pfx, senha);

        pfx[0] = 0;
        senha[0] = 'X';
        assertThat(pedido.pfx()).containsExactly(PFX);
        assertThat(pedido.senha()).containsExactly(SENHA);

        pedido.pfx()[1] = 0;
        pedido.senha()[1] = 'Y';
        assertThat(pedido.pfx()).containsExactly(PFX);
        assertThat(pedido.senha()).containsExactly(SENHA);
    }

    @Test
    @DisplayName("limpar() zera o pfx e a senha")
    void limparZeraPfxESenha() {
        var pedido = pedido(PFX.clone(), SENHA.clone());

        pedido.limpar();

        assertThat(pedido.pfx()).containsOnly((byte) 0).hasSize(PFX.length);
        assertThat(pedido.senha()).containsOnly('\0').hasSize(SENHA.length);
    }

    @Test
    @DisplayName("toString não mostra pfx nem senha")
    void toStringNaoMostraPfxNemSenha() {
        var texto = pedido(PFX.clone(), SENHA.clone()).toString();

        assertThat(texto).doesNotContain("segredo");
        assertThat(texto).doesNotContain(new String(SENHA));
        assertThat(texto).doesNotContain(java.util.Arrays.toString(PFX));
        assertThat(texto).doesNotContain("[B@").doesNotContain("[C@");
    }

    @Test
    @DisplayName("rejeita nulos, pfx vazio e tenant ou titular não positivos; senha vazia é válida")
    void rejeitaNulosPfxVazioETenantOuTitularNaoPositivos() {
        assertThatThrownBy(() -> new PedidoGuarda(0L, 2L, CNPJ, PFX, SENHA, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(-1L, 2L, CNPJ, PFX, SENHA, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 0L, CNPJ, PFX, SENHA, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 2L, null, PFX, SENHA, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 2L, CNPJ, null, SENHA, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 2L, CNPJ, new byte[0], SENHA, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 2L, CNPJ, PFX, null, "operador"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 2L, CNPJ, PFX, SENHA, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoGuarda(1L, 2L, CNPJ, PFX, SENHA, " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> new PedidoGuarda(1L, 2L, CNPJ, PFX, new char[0], "operador"))
                .doesNotThrowAnyException();
    }
}
