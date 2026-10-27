package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Traduz cStat da SEFAZ para o vocabulario unico de Desfecho.
 *
 * <p>Este e o ponto onde o dialeto SEFAZ vira linguagem do core.
 * O core NUNCA ve cStat - so ve Desfecho.
 *
 * <p><b>Mapeamentos principais (distDFe / consNSU):</b>
 * <ul>
 *   <li><b>138</b> - documento localizado -> {@link Desfecho.Ok}</li>
 *   <li><b>137</b> - nenhum documento encontrado -> {@link Desfecho.Vazio}</li>
 *   <li><b>108</b> - servico paralisado momentaneamente -> {@link Desfecho.IndisponivelTemporario}</li>
 *   <li><b>656</b> - consumo indevido -> {@link Desfecho.LimiteAtingido} (CRITICO!)</li>
 *   <li><b>632</b> - excesso de consultas -> {@link Desfecho.LimiteAtingido}</li>
 *   <li><b>109</b> - servico paralisado -> {@link Desfecho.ServicoFora}</li>
 * </ul>
 *
 * <p>cStat desconhecido/nulo/vazio -> {@link Desfecho.IndisponivelTemporario}
 * (fallback seguro - prefere tentar de novo a assumir que deu certo).
 */
public class CStatMapper {

    private static final Logger LOG = LoggerFactory.getLogger(CStatMapper.class);

    /** Tamanho maximo do cStat bruto no log (inv. 13: nunca vazar XML). */
    private static final int MAX_CSTAT_NO_LOG = 16;

    /** Retry padrao para cStat de limite (656). */
    private static final Duration RETRY_PADRAO = Duration.ofHours(1);

    /** Retry padrao para cStat desconhecido/indisponivel. */
    private static final Duration RETRY_TEMP = Duration.ofMinutes(5);

    /**
     * Mapeia um cStat da SEFAZ para um Desfecho normalizado.
     *
     * @param cStat o codigo de status (ex: "138", "656")
     * @return o Desfecho correspondente
     */
    public Desfecho mapear(String cStat) {
        if (cStat == null || cStat.isBlank()) {
            return fallback(cStat);
        }

        return switch (cStat) {
            // Sucesso
            case "138" -> new Desfecho.Ok();

            // Vazio - nada novo
            case "137" -> new Desfecho.Vazio();

            // Consumo indevido - NAO avancar ponteiro
            case "656" -> new Desfecho.LimiteAtingido(RETRY_PADRAO);

            // NF-e fora do prazo de distribuicao (MOC 7.0 Tab. 5-29 H15, so consChNFe) - nao retentar
            case "632" -> new Desfecho.IndisponivelDefinitivo("Documento fora de prazo de distribuição (cStat 632)");

            // NSU maior que o maxNSU (MOC 7.0 Tab. 5-29 H06) - erro de consistencia, nunca retentar
            case "589" -> new Desfecho.IndisponivelDefinitivo("Cursor inconsistente: NSU > maxNSU (cStat 589)");

            // Servico paralisado momentaneamente (MOC 7.0 Tab. 5-20 K03) - tentar de novo
            case "108" -> new Desfecho.IndisponivelTemporario(RETRY_TEMP);

            // Servico paralisado sem previsao (MOC 7.0 Tab. 5-20 K03)
            case "109" -> new Desfecho.ServicoFora("Serviço paralisado sem previsão (cStat 109)");

            // Desconhecido - fallback seguro
            default -> fallback(cStat);
        };
    }

    private Desfecho fallback(String cStat) {
        LOG.warn("cStat nao mapeado, usando fallback IndisponivelTemporario: [{}]", sanitizar(cStat));
        return new Desfecho.IndisponivelTemporario(RETRY_TEMP);
    }

    /** So letras e digitos, truncado: o log nunca carrega XML nem texto livre da resposta. */
    private static String sanitizar(String cStat) {
        if (cStat == null) {
            return "null";
        }
        String limpo = cStat.replaceAll("[^A-Za-z0-9]", "");
        return limpo.length() > MAX_CSTAT_NO_LOG ? limpo.substring(0, MAX_CSTAT_NO_LOG) : limpo;
    }
}
