package br.com.doc.ingest.domain.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Credencial (bytes do pfx + senha, só em memória)")
class CredencialTest {

    private static final byte[] PFX = {0x30, (byte) 0x82, 0x01, 0x02, 0x7f};
    private static final char[] SENHA = "segredo-sintetico".toCharArray();

    @Test
    @DisplayName("faz cópia defensiva na entrada e nos acessores")
    void copiaDefensivaDosBytesEDaSenha() {
        byte[] pfx = PFX.clone();
        char[] senha = SENHA.clone();
        var credencial = new Credencial(pfx, senha);

        pfx[0] = 0;
        senha[0] = 'X';
        assertThat(credencial.pfx()).containsExactly(PFX);
        assertThat(credencial.senha()).containsExactly(SENHA);

        credencial.pfx()[1] = 0;
        credencial.senha()[1] = 'Y';
        assertThat(credencial.pfx()).containsExactly(PFX);
        assertThat(credencial.senha()).containsExactly(SENHA);
    }

    @Test
    @DisplayName("rejeita pfx ou senha nulos e pfx vazio")
    void rejeitaPfxOuSenhaNulosOuPfxVazio() {
        assertThatThrownBy(() -> new Credencial(null, SENHA.clone()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Credencial(PFX.clone(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Credencial(new byte[0], SENHA.clone()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("limpar() zera os bytes do pfx e a senha")
    void limparZeraBytesESenha() {
        var credencial = new Credencial(PFX.clone(), SENHA.clone());

        credencial.limpar();

        assertThat(credencial.pfx()).containsOnly((byte) 0).hasSize(PFX.length);
        assertThat(credencial.senha()).containsOnly('\0').hasSize(SENHA.length);
    }

    @Test
    @DisplayName("toString não mostra bytes nem senha, só o tamanho do pfx")
    void toStringNaoMostraBytesNemSenha() {
        var texto = new Credencial(PFX.clone(), SENHA.clone()).toString();

        assertThat(texto).contains(String.valueOf(PFX.length));
        assertThat(texto).doesNotContain("segredo");
        assertThat(texto).doesNotContain(new String(SENHA));
        assertThat(texto).doesNotContain(java.util.Arrays.toString(PFX));
        assertThat(texto).doesNotContain("[48");
    }

    @Test
    @DisplayName("equals e hashCode comparam o conteúdo dos arrays")
    void equalsPorConteudo() {
        var a = new Credencial(PFX.clone(), SENHA.clone());
        var b = new Credencial(PFX.clone(), SENHA.clone());
        var outraSenha = new Credencial(PFX.clone(), "outra".toCharArray());
        var outroPfx = new Credencial(new byte[]{1, 2, 3}, SENHA.clone());

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(outraSenha);
        assertThat(a).isNotEqualTo(outroPfx);
    }
}
