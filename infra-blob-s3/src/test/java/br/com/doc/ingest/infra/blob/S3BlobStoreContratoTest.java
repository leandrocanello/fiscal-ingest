package br.com.doc.ingest.infra.blob;

import br.com.doc.ingest.adapter.contrato.BlobStoreContrato;
import br.com.doc.ingest.domain.blob.BlobStore;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.s3.S3Client;

class S3BlobStoreContratoTest extends BlobStoreContrato {

    private S3Client s3;

    @Override
    protected BlobStore novoBlobStore() {
        s3 = SeaweedFsTestBase.novoCliente();
        return new S3BlobStore(s3, SeaweedFsTestBase.novoBucket(s3));
    }

    @AfterEach
    void fecharCliente() {
        if (s3 != null) {
            s3.close();
        }
    }
}
