package br.com.doc.ingest.domain.cofre;

/** Por que um .pfx foi recusado no upload. Nunca carrega conteúdo do arquivo nem a senha. */
public enum MotivoRecusa {
    TAMANHO_EXCEDIDO,
    ILEGIVEL,
    SENHA_INCORRETA,
    SEM_CHAVE_PRIVADA,
    AMBIGUO,
    FORA_DA_VALIDADE,
    CADEIA_NAO_CONFIAVEL,
    SEM_CNPJ,
    CNPJ_DIVERGENTE
}
