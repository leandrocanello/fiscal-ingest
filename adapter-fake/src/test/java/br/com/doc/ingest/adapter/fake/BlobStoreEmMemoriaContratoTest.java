package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.adapter.contrato.BlobStoreContrato;
import br.com.doc.ingest.domain.blob.BlobStore;

class BlobStoreEmMemoriaContratoTest extends BlobStoreContrato {

    @Override
    protected BlobStore novoBlobStore() {
        return new BlobStoreEmMemoria();
    }
}
