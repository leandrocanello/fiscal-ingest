package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** {@link BlobStore} em memória, apenas para testes. */
public class BlobStoreEmMemoria implements BlobStore {

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

    @Override
    public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
        BlobRef ref = new BlobRef(chave, sha256);
        objetos.put(chave, conteudo.clone());
        return ref;
    }

    @Override
    public Optional<byte[]> ler(BlobRef ref) {
        byte[] conteudo = objetos.get(ref.chave());
        if (conteudo == null || !br.com.doc.ingest.domain.blob.Sha256.hex(conteudo).equals(ref.sha256())) {
            return Optional.empty();
        }
        return Optional.of(conteudo.clone());
    }
}
