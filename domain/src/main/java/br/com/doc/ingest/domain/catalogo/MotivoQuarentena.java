package br.com.doc.ingest.domain.catalogo;

/** Por que um arquivo ficou em quarentena (§7.3). */
public enum MotivoQuarentena {
    /** Nenhuma empresa do tenant aparece no conteúdo; guardado até a empresa ser cadastrada. */
    SEM_EMPRESA
}
