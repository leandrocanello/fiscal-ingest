package br.com.doc.ingest.adapter.nfe.cert;

/** Por que um .pfx não pôde ser carregado. */
public enum MotivoCertificadoInvalido {
    /** A senha não confere com o MAC ou com a cifra do .pfx. */
    SENHA_INCORRETA,
    /** Os bytes não formam um PKCS#12 legível. */
    ILEGIVEL,
    /** O .pfx não contém nenhuma chave privada. */
    SEM_CHAVE_PRIVADA,
    /** O .pfx contém mais de uma chave privada; não há titular claro. */
    AMBIGUO
}
