package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.adapter.BuscarUmResultado;
import br.com.doc.ingest.domain.adapter.ConsultaPontualPort;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.ledger.EntradaLedger;
import br.com.doc.ingest.domain.ledger.GapCalculator;
import br.com.doc.ingest.domain.ledger.NsuLedger;
import br.com.doc.ingest.domain.ledger.StatusNsu;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Caso de uso que orquestra a recuperacao de NSUs faltantes (gaps).
 *
 * Fluxo:
 * 1. Lista entradas do ledger para (fonte, cnpj).
 * 2. Usa GapCalculator para identificar buracos na sequencia.
 * 3. Para cada NSU faltante, chama buscarUm (consulta pontual).
 * 4. Se Ok -> registra no ledger como BAIXADO/RESUMO.
 * 5. Se IndisponivelDefinitivo -> registra FORA_PRAZO.
 * 6. Se LimiteAtingido -> para e devolve NSUs restantes como pendentes.
 */
public class RecuperarGapUseCase {

    private final NsuLedger ledger;
    private final ConsultaPontualPort consulta;
    private final GapCalculator gapCalculator;

    public RecuperarGapUseCase(NsuLedger ledger, ConsultaPontualPort consulta) {
        this.ledger = ledger;
        this.consulta = consulta;
        this.gapCalculator = new GapCalculator();
    }

    public ResultadoRecuperacao executar(FonteId fonte, Cnpj cnpj) {
        List<EntradaLedger> entradas = ledger.listarEntradas(fonte, cnpj);
        Set<Nsu> gaps = gapCalculator.calcular(entradas);

        if (gaps.isEmpty()) {
            return ResultadoRecuperacao.vazio();
        }

        List<Nsu> gapsOrdenados = new ArrayList<>(gaps);
        gapsOrdenados.sort(Nsu::compareTo);

        List<Nsu> recuperados = new ArrayList<>();
        List<Nsu> foraDoPrazo = new ArrayList<>();
        List<Nsu> pendentes = new ArrayList<>();

        boolean parou = false;

        for (Nsu nsu : gapsOrdenados) {
            if (parou) {
                pendentes.add(nsu);
                continue;
            }

            BuscarUmResultado resultado = consulta.buscarUm(fonte, cnpj, nsu);

            switch (resultado.desfecho()) {
                case Desfecho.Ok ok -> {
                    String tipoConteudo = resultado.documento()
                            .map(d -> d.tipoConteudo())
                            .orElse("COMPLETO");
                    StatusNsu status = mapearStatus(tipoConteudo);
                    ledger.registrar(fonte, cnpj, new EntradaLedger(nsu, status));
                    recuperados.add(nsu);
                }
                case Desfecho.Vazio vazio -> {
                    ledger.registrar(fonte, cnpj, new EntradaLedger(nsu, StatusNsu.DESCARTADO));
                    recuperados.add(nsu);
                }
                case Desfecho.IndisponivelDefinitivo id -> {
                    ledger.registrar(fonte, cnpj, new EntradaLedger(nsu, StatusNsu.FORA_PRAZO));
                    foraDoPrazo.add(nsu);
                }
                case Desfecho.LimiteAtingido la -> {
                    pendentes.add(nsu);
                    parou = true;
                }
                case Desfecho.IndisponivelTemporario it -> {
                    pendentes.add(nsu);
                    parou = true;
                }
                case Desfecho.ErroAutenticacao ea -> {
                    pendentes.add(nsu);
                    parou = true;
                }
                case Desfecho.ServicoFora sf -> {
                    pendentes.add(nsu);
                    parou = true;
                }
            }
        }

        return new ResultadoRecuperacao(
                List.copyOf(recuperados),
                List.copyOf(pendentes),
                List.copyOf(foraDoPrazo)
        );
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
