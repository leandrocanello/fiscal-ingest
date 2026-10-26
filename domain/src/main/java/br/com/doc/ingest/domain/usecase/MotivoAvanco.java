package br.com.doc.ingest.domain.usecase;

/**
 * Por que o {@link AvancarPonteiroUseCase} avançou (ou não) a posição.
 */
public enum MotivoAvanco {
    /** Ok/Vazio com posição nova maior que a atual. */
    AVANCOU,
    /** Desfecho de parada, ou posição igual à atual. */
    NAO_AVANCOU,
    /** Posição nova menor que a atual: erro de consistência, nunca retentar. */
    RETROCESSO_REJEITADO
}
