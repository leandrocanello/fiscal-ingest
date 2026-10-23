package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** {@link BlobStore} em memória, apenas para testes. */
public class BlobStoreEmMemoria implements BlobStore {

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

    @Override
    public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
        BlobRef ref = new BlobRef(chave, sha256);
        byte[] copia = conteudo.clone();
        objetos.compute(chave, (k, existente) -> {
            if (existente == null) {
                return copia;
            }
            String shaExistente = Sha256.hex(existente);
            if (!shaExistente.equals(sha256)) {
                throw new BlobConflitanteException(chave, shaExistente, sha256);
            }
            return existente;
        });
        return ref;
    }

    @Override
    public Optional<byte[]> ler(BlobRef ref) {
        byte[] conteudo = objetos.get(ref.chave());
        if (conteudo == null || !Sha256.hex(conteudo).equals(ref.sha256())) {
            return Optional.empty();
        }
        return Optional.of(conteudo.clone());
    }
}
