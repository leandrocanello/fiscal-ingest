package br.com.doc.ingest.adapter.nfe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guarda da inv. 14 e da DA-7: o endpoint só entra por configuração, nunca embutido no código. */
class SemUrlDeSefazNoCodigoTest {

    private static final List<String> PROIBIDOS = List.of("fazenda.gov.br", ".asmx", "?wsdl");

    @Test
    void nenhumaUrlDeSefazNoCodigo() throws IOException {
        Path raiz = Path.of("src/main/java");
        assertThat(raiz).isDirectory();

        List<String> achados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String conteudo = Files.readString(arquivo);
                for (String proibido : PROIBIDOS) {
                    if (conteudo.contains(proibido)) {
                        achados.add(arquivo + " contém " + proibido);
                    }
                }
            }
        }
        assertThat(achados).isEmpty();
    }
}
