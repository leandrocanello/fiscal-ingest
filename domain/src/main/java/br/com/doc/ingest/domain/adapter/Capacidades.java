package br.com.doc.ingest.domain.adapter;

/**
 * Declara o que o adapter sabe fazer.
 * O core consulta isto para decidir se pode usar lote, pontual, manifestacao, etc.
 *
 * @param fazLote          suporta buscarLote (ex: distNSU)
 * @param fazPontual       suporta buscarUm (ex: consNSU)
 * @param fazManifestacao  suporta manifestar (ex: ciencia da operacao)
 * @param permiteRetrocesso se a fonte permite voltar NSU (SEFAZ AN = false - CRITICO p/ 656)
 */
public record Capacidades(
    boolean fazLote,
    boolean fazPontual,
    boolean fazManifestacao,
    boolean permiteRetrocesso
) {}
