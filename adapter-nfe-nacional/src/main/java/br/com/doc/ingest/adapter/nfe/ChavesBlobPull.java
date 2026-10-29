package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Layout das chaves de blob da coleta pull (DA-13). Determinístico e sempre com o sha256 do conteúdo
 * (invariante 7). Sem tenant e sem gzip por ora (ver PARA-VOCE).
 *
 * <pre>
 * raw/pull/{fonte}/{ambiente}/{cnpj}/{aaaa}/{mm}/{dd}/{sha256}.envelope.xml   (data em UTC)
 * raw/pull/{fonte}/{ambiente}/{cnpj}/nsu/{nsu com 15 dígitos}-{sha256}.xml
 * </pre>
 */
public final class ChavesBlobPull {

    private ChavesBlobPull() {
    }

    public static String envelope(FonteId fonte, TipoAmbiente ambiente, Cnpj cnpj, Instant recebidoEm, String sha256) {
        var data = recebidoEm.atOffset(ZoneOffset.UTC).toLocalDate();
        return String.format(Locale.ROOT, "%s/%04d/%02d/%02d/%s.envelope.xml",
                prefixo(fonte, ambiente, cnpj), data.getYear(), data.getMonthValue(), data.getDayOfMonth(), sha256);
    }

    public static String documento(FonteId fonte, TipoAmbiente ambiente, Cnpj cnpj, Nsu nsu, String sha256) {
        return String.format(Locale.ROOT, "%s/nsu/%015d-%s.xml", prefixo(fonte, ambiente, cnpj), nsu.valor(), sha256);
    }

    private static String prefixo(FonteId fonte, TipoAmbiente ambiente, Cnpj cnpj) {
        return "raw/pull/" + fonte.id() + "/" + ambiente.name() + "/" + cnpj.valor();
    }
}
