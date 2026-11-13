package br.com.doc.ingest.adapter.nfe.smoke;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Cursor do smoke guardado em {@code cursor-<cnpj>.txt}. Recusa NSU menor que o salvo e nunca
 * volta a 0 em silêncio se o arquivo estiver corrompido (inv. 1).
 */
public final class CursorLocal {

    private final Path arquivo;
    private final Path temporario;

    public CursorLocal(Path dir, Cnpj cnpj) {
        this.arquivo = dir.resolve("cursor-" + cnpj.valor() + ".txt");
        this.temporario = dir.resolve("cursor-" + cnpj.valor() + ".txt.tmp");
    }

    public Nsu ler() {
        if (!Files.exists(arquivo)) {
            return new Nsu(0);
        }
        try {
            return new Nsu(Long.parseLong(Files.readString(arquivo).trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("arquivo de cursor corrompido: " + arquivo.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void gravar(Nsu novo) {
        Nsu salvo = ler();
        if (novo.ehMenorQue(salvo)) {
            throw new IllegalStateException(
                    "recusado: NSU " + novo.valor() + " é menor que o salvo " + salvo.valor());
        }
        try {
            Files.writeString(temporario, Long.toString(novo.valor()));
            Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
