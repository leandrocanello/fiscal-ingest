package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.NsuLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * Caso de uso que avanca o ponteiro (cursor) com base no Desfecho.
 *
 * Regras criticas:
 * - Ok / Vazio -> avanca cursor + persiste entradas no ledger
 * - LimiteAtingido -> NAO avanca (cStat 656)
 * - IndisponivelTemporario -> NAO avanca (retry sem mover ponteiro)
 */
public class AvancarPonteiroUseCase {

    private final NsuLedger ledger;

    public AvancarPonteiroUseCase(NsuLedger ledger) {
        this.ledger = ledger;
    }

    public ResultadoAvanco executar(FonteId fonte, Cnpj cnpj, Cursor cursorAtual, Nsu ultNsuAtual,
                                   BuscarLoteResultado resultado) {
        Desfecho desfecho = resultado.desfecho();

        return switch (desfecho) {
            case Desfecho.Ok ok -> {
                registrarDocumentos(fonte, cnpj, resultado);
                Cursor novoCursor = resultado.proximoCursor();
                yield new ResultadoAvanco(novoCursor, resultado.ultNsu(), true);
            }
            case Desfecho.Vazio vazio -> {
                Cursor novoCursor = resultado.proximoCursor();
                yield new ResultadoAvanco(novoCursor, resultado.ultNsu(), true);
            }
            // NAO avanca em nenhum destes casos (protecao cStat 656)
            case Desfecho.LimiteAtingido la -> new ResultadoAvanco(cursorAtual, ultNsuAtual, false);
            case Desfecho.IndisponivelTemporario it -> new ResultadoAvanco(cursorAtual, ultNsuAtual, false);
            case Desfecho.IndisponivelDefinitivo id -> new ResultadoAvanco(cursorAtual, ultNsuAtual, false);
            case Desfecho.ErroAutenticacao ea -> new ResultadoAvanco(cursorAtual, ultNsuAtual, false);
            case Desfecho.ServicoFora sf -> new ResultadoAvanco(cursorAtual, ultNsuAtual, false);
        };
    }

    private void registrarDocumentos(FonteId fonte, Cnpj cnpj, BuscarLoteResultado resultado) {
        for (Documento doc : resultado.documentos()) {
            StatusNsu status = mapearStatus(doc.tipoConteudo());
            EntradaLedger entrada = new EntradaLedger(doc.nsu(), status);
            ledger.registrar(fonte, cnpj, entrada);
        }
    }

    private StatusNsu mapearStatus(String tipoConteudo) {
        return switch (tipoConteudo) {
            case "RESUMO" -> StatusNsu.RESUMO;
            case "COMPLETO" -> StatusNsu.BAIXADO;
            case "EVENTO" -> StatusNsu.EVENTO;
            default -> StatusNsu.DESCARTADO;
        };
    }
}
