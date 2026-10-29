package br.com.doc.ingest.adapter.nfe.transporte;

import br.com.doc.ingest.domain.adapter.Credencial;

/**
 * Cria o {@link TransporteSoap} de uma credencial (mTLS em produção; HTTP simples nos testes com WireMock).
 * Quem implementa não deve reter a {@link Credencial}.
 */
@FunctionalInterface
public interface FornecedorTransporte {

    TransporteSoap para(Credencial credencial);
}
