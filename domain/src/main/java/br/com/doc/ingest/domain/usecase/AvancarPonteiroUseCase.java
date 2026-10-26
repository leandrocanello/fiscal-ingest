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
 * <p>Regras criticas (invariante 1 — nao-retrocesso de NSU):
 * <ul>
 *   <li>So avanca se novo &ge; atual ({@code resultado.ultNsu()} comparado com {@code ultNsuAtual}).
 *       Novo &gt; atual em Ok/Vazio = {@link MotivoAvanco#AVANCOU}; igual = {@link MotivoAvanco#NAO_AVANCOU}
 *       (em Ok, os documentos ainda sao registrados, de forma idempotente — ADR-005).</li>
 *   <li>Recuo (novo &lt; atual) = {@link MotivoAvanco#RETROCESSO_REJEITADO}: erro de consistencia, nunca
 *       retentar; nada e registrado no ledger e cursor/NSU de entrada sao devolvidos.</li>
 *   <li>LimiteAtingido, IndisponivelTemporario, IndisponivelDefinitivo, ErroAutenticacao, ServicoFora
 *       -> NAO avanca e nao registra nada.</li>
 * </ul>
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
                if (resultado.ultNsu().ehMenorQue(ultNsuAtual)) {
                    yield new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.RETROCESSO_REJEITADO);
                }
                registrarDocumentos(fonte, cnpj, resultado);
                yield avancarSeMaior(cursorAtual, ultNsuAtual, resultado);
            }
            case Desfecho.Vazio vazio -> {
                if (resultado.ultNsu().ehMenorQue(ultNsuAtual)) {
                    yield new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.RETROCESSO_REJEITADO);
                }
                yield avancarSeMaior(cursorAtual, ultNsuAtual, resultado);
            }
            // NAO avanca em nenhum destes casos (protecao cStat 656)
            case Desfecho.LimiteAtingido la -> new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.NAO_AVANCOU);
            case Desfecho.IndisponivelTemporario it -> new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.NAO_AVANCOU);
            case Desfecho.IndisponivelDefinitivo id -> new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.NAO_AVANCOU);
            case Desfecho.ErroAutenticacao ea -> new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.NAO_AVANCOU);
            case Desfecho.ServicoFora sf -> new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.NAO_AVANCOU);
        };
    }

    private ResultadoAvanco avancarSeMaior(Cursor cursorAtual, Nsu ultNsuAtual, BuscarLoteResultado resultado) {
        if (resultado.ultNsu().ehMaiorQue(ultNsuAtual)) {
            return new ResultadoAvanco(resultado.proximoCursor(), resultado.ultNsu(), MotivoAvanco.AVANCOU);
        }
        return new ResultadoAvanco(cursorAtual, ultNsuAtual, MotivoAvanco.NAO_AVANCOU);
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
