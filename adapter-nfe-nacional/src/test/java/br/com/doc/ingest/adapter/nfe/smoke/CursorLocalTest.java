package br.com.doc.ingest.adapter.nfe.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CursorLocal (cursor do smoke em arquivo; recusa NSU menor — inv. 1)")
class CursorLocalTest {

    private static final Cnpj CNPJ = new Cnpj("11222333000181");

    @TempDir
    Path dir;

    @Test
    void semArquivoLeNsuZero() {
        assertThat(new CursorLocal(dir, CNPJ).ler()).isEqualTo(new Nsu(0));
    }

    @Test
    void gravarMaiorOuIgualPersisteNoArquivoDoCnpj() throws Exception {
        var cursor = new CursorLocal(dir, CNPJ);
        cursor.gravar(new Nsu(10));
        cursor.gravar(new Nsu(10));
        cursor.gravar(new Nsu(12));

        assertThat(new CursorLocal(dir, CNPJ).ler()).isEqualTo(new Nsu(12));
        assertThat(Files.exists(dir.resolve("cursor-11222333000181.txt"))).isTrue();
        try (var arquivos = Files.list(dir)) {
            assertThat(arquivos).hasSize(1);
        }
    }

    @Test
    void gravarMenorQueOSalvoFalhaEMantemOArquivo() throws Exception {
        var cursor = new CursorLocal(dir, CNPJ);
        cursor.gravar(new Nsu(50));

        assertThatThrownBy(() -> cursor.gravar(new Nsu(49))).isInstanceOf(IllegalStateException.class);

        assertThat(Files.readString(dir.resolve("cursor-11222333000181.txt")).trim()).isEqualTo("50");
    }

    @Test
    void arquivoCorrompidoFalhaSemVoltarParaZero() throws Exception {
        Files.writeString(dir.resolve("cursor-11222333000181.txt"), "lixo");
        var cursor = new CursorLocal(dir, CNPJ);

        assertThatThrownBy(cursor::ler).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cursor.gravar(new Nsu(1))).isInstanceOf(IllegalStateException.class);
    }
}
