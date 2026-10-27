package br.com.doc.ingest.adapter.nfe.transporte;

import java.net.URI;

/** Porta interna do adapter: envia um envelope SOAP 1.2 e devolve a resposta sem interpretá-la. */
public interface TransporteSoap {

    ResultadoTransporte enviar(URI endpoint, String action, byte[] envelope);
}
