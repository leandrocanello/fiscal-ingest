package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;

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

    /** Retry padrao para cStat de limite (656/632). */
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
            return new Desfecho.IndisponivelTemporario(RETRY_TEMP);
        }

        return switch (cStat) {
            // Sucesso
            case "138" -> new Desfecho.Ok();

            // Vazio - nada novo
            case "137" -> new Desfecho.Vazio();

            // Limite atingido - NAO avancar ponteiro (cStat 656/632)
            case "656", "632" -> new Desfecho.LimiteAtingido(RETRY_PADRAO);

            // Servico paralisado momentaneamente (MOC 7.0 Tab. 5-20 K03) - tentar de novo
            case "108" -> new Desfecho.IndisponivelTemporario(RETRY_TEMP);

            // Servico fora
            case "109" -> new Desfecho.ServicoFora("Servico paralisado (cStat 109)");

            // Desconhecido - fallback seguro
            default -> new Desfecho.IndisponivelTemporario(RETRY_TEMP);
        };
    }
}
