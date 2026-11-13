package br.com.doc.ingest.domain.parser;

/**
 * Porta de parse de um DF-e (§9, DA-32): classifica o bruto pelo schema e extrai só metadado.
 *
 * <p>O XML entra só como parâmetro e nunca fica em tipo do domínio (inv. 3). A implementação usa
 * parser sem DTD nem entidades externas (inv. 9) e nunca lança por conteúdo ruim: devolve
 * {@link ResultadoParse.Invalido}, com motivo que não ecoa o conteúdo (inv. 13).
 */
public interface ParserDfe {

    /**
     * @param schema schema informado pela origem (ex.: o atributo {@code schema} do {@code docZip})
     * @param xml    bytes do documento; não são alterados
     */
    ResultadoParse analisar(String schema, byte[] xml);
}
