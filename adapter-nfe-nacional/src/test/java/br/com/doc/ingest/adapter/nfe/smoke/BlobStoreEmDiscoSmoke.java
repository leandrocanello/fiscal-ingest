package br.com.doc.ingest.adapter.nfe.smoke;

import br.com.doc.ingest.domain.blob.BlobConflitanteException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * {@link BlobStore} do smoke: grava em {@code <dir>/<AAAA-MM-DD>/<chave saneada>}. Recusa pasta dentro
 * de um repositório git, para nenhum XML real ir parar num commit.
 */
public final class BlobStoreEmDiscoSmoke implements BlobStore {

    private final Path dir;
    private final Clock clock;

    public BlobStoreEmDiscoSmoke(Path dir, Clock clock) {
        Path absoluto = dir.toAbsolutePath().normalize();
        for (Path p = absoluto; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"))) {
                throw new IllegalStateException("pasta do smoke está dentro de um repositório (.git em " + p + ")");
            }
        }
        this.dir = absoluto;
        this.clock = clock;
    }

    private static String sanear(String chave) {
        return chave.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path pastaDoDia() {
        return dir.resolve(LocalDate.now(clock).toString());
    }

    @Override
    public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
        BlobRef ref = new BlobRef(chave, sha256);
        if (!Sha256.hex(conteudo).equals(sha256)) {
            throw new IllegalArgumentException("sha256 informado não confere com o conteúdo para a chave " + chave);
        }
        try {
            Path pasta = pastaDoDia();
            Files.createDirectories(pasta);
            Path alvo = pasta.resolve(sanear(chave));
            if (Files.exists(alvo)) {
                String existente = Sha256.hex(Files.readAllBytes(alvo));
                if (!existente.equals(sha256)) {
                    throw new BlobConflitanteException(chave, existente, sha256);
                }
                return ref;
            }
            Path tmp = pasta.resolve(sanear(chave) + ".tmp");
            Files.write(tmp, conteudo);
            Files.move(tmp, alvo, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return ref;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> ler(BlobRef ref) {
        Path alvo = pastaDoDia().resolve(sanear(ref.chave()));
        try {
            if (!Files.exists(alvo)) {
                return Optional.empty();
            }
            byte[] c = Files.readAllBytes(alvo);
            return Sha256.hex(c).equals(ref.sha256()) ? Optional.of(c) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
