package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import br.com.doc.ingest.domain.outbox.MensagemOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de {@link ConfirmacaoLote}: o que o fetcher entrega ao repositório para confirmar
 * um lote por CAS (ledger + cursor na mesma transação, CLAUDE.md §6.3 passo 4).
 */
@DisplayName("ConfirmacaoLote (domínio da coleta)")
class ConfirmacaoLoteTest {

    private static final ChaveColeta CHAVE = new ChaveColeta(
            new FonteId("NFE_AN"), new Cnpj("12345678000195"), Ambiente.HOMOLOGACAO);
    private static final PosicaoColeta LIDA =
            new PosicaoColeta(CHAVE, Cursor.inicial(), new Nsu(0), 0L);

    @Test
    @DisplayName("rejeita entrada com NSU maior que o novo ultNsu")
    void rejeitaEntradaComNsuMaiorQueNovoUltNsu() {
        var entradas = List.of(
                new EntradaLedger(new Nsu(5), StatusNsu.BAIXADO),
                new EntradaLedger(new Nsu(6), StatusNsu.RESUMO));

        assertThatThrownBy(() -> new ConfirmacaoLote(LIDA, new Cursor("5"), new Nsu(5), entradas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NSU");
    }

    @Test
    @DisplayName("rejeita nulos")
    void rejeitaNulos() {
        var cursor = new Cursor("1");
        var nsu = new Nsu(1);
        List<EntradaLedger> vazia = List.of();

        assertThatThrownBy(() -> new ConfirmacaoLote(null, cursor, nsu, vazia))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfirmacaoLote(LIDA, null, nsu, vazia))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfirmacaoLote(LIDA, cursor, null, vazia))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfirmacaoLote(LIDA, cursor, nsu, null))
                .isInstanceOf(NullPointerException.class);
        var comNulo = new ArrayList<EntradaLedger>();
        comNulo.add(null);
        assertThatThrownBy(() -> new ConfirmacaoLote(LIDA, cursor, nsu, comNulo))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ChaveColeta(null, CHAVE.cnpj(), CHAVE.ambiente()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChaveColeta(CHAVE.fonte(), null, CHAVE.ambiente()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChaveColeta(CHAVE.fonte(), CHAVE.cnpj(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PosicaoColeta(null, Cursor.inicial(), nsu, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PosicaoColeta(CHAVE, null, nsu, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PosicaoColeta(CHAVE, Cursor.inicial(), null, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("aceita lista vazia (Vazio/137 avança o cursor sem entradas)")
    void aceitaListaVazia() {
        var confirmacao = new ConfirmacaoLote(LIDA, new Cursor("0"), new Nsu(0), List.of());

        assertThat(confirmacao.entradas()).isEmpty();
        assertThat(confirmacao.lida()).isEqualTo(LIDA);
    }

    @Test
    @DisplayName("guarda cópia imutável das entradas")
    void guardaCopiaImutavelDasEntradas() {
        var entradas = new ArrayList<EntradaLedger>();
        entradas.add(new EntradaLedger(new Nsu(1), StatusNsu.BAIXADO));
        var confirmacao = new ConfirmacaoLote(LIDA, new Cursor("1"), new Nsu(1), entradas);

        entradas.add(new EntradaLedger(new Nsu(2), StatusNsu.BAIXADO));

        assertThat(confirmacao.entradas()).hasSize(1);
        assertThatThrownBy(() -> confirmacao.entradas().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rejeita versão negativa na posição lida")
    void rejeitaVersaoNegativa() {
        assertThatThrownBy(() -> new PosicaoColeta(CHAVE, Cursor.inicial(), new Nsu(0), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ResultadoCas é sealed com os três desfechos do CAS")
    void resultadoCasTemTresDesfechos() {
        ResultadoCas r = new ResultadoCas.Aplicado(1L);
        String descricao = switch (r) {
            case ResultadoCas.Aplicado a -> "aplicado " + a.novaVersao();
            case ResultadoCas.ConflitoDeVersao c -> "conflito " + c.versaoAtual();
            case ResultadoCas.RetrocessoRecusado rr -> "retrocesso " + rr.ultNsuAtual();
        };
        assertThat(descricao).isEqualTo("aplicado 1");
    }

    @Test
    @DisplayName("guarda cópia imutável das mensagens do outbox")
    void mensagensSaoCopiaImutavel() {
        var mensagens = new ArrayList<MensagemOutbox>();
        mensagens.add(new MensagemOutbox("docs.coletados", "k", 1, "id-1", "{}"));
        var confirmacao = new ConfirmacaoLote(LIDA, new Cursor("1"), new Nsu(1), List.of(), mensagens);

        mensagens.add(new MensagemOutbox("docs.coletados", "k", 1, "id-2", "{}"));

        assertThat(confirmacao.mensagens()).hasSize(1);
        assertThatThrownBy(() -> confirmacao.mensagens().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("construtor sem mensagens equivale à lista vazia")
    void construtorSemMensagensEquivaleAListaVazia() {
        var confirmacao = new ConfirmacaoLote(LIDA, new Cursor("0"), new Nsu(0), List.of());

        assertThat(confirmacao.mensagens()).isEmpty();
    }
}
