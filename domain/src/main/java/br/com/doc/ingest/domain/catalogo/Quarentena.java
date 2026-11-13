package br.com.doc.ingest.domain.catalogo;

/**
 * Porta da quarentena (§7.3): arquivo do catálogo sem empresa do tenant fica guardado e sinalizado,
 * nunca descartado. No máximo uma quarentena aberta por arquivo; resolver só fecha (inv. 6).
 */
public interface Quarentena {

    /**
     * Abre a quarentena do arquivo, se ainda não houver uma aberta e se o documento do arquivo continuar
     * sem vínculo com empresa. A conferência do vínculo é atômica com a abertura: um vínculo gravado em
     * paralelo nunca deixa quarentena aberta para trás.
     *
     * @return {@code true} se abriu agora; {@code false} se já estava aberta ou se o documento já tem vínculo
     * @throws FalhaCatalogoException se a operação falhar; nada fica gravado
     */
    boolean abrir(long tenantId, long arquivoId, MotivoQuarentena motivo);

    /**
     * Fecha as quarentenas abertas de todos os arquivos do documento.
     *
     * @return quantas foram fechadas; zero se não havia nenhuma aberta
     * @throws FalhaCatalogoException se a operação falhar; nada fica gravado
     */
    int resolverDoDocumento(long tenantId, long documentoId);
}
