package br.com.doc.ingest.adapter.nfe.smoke;

import br.com.doc.ingest.adapter.nfe.ChaveAcessoNfe;
import br.com.doc.ingest.adapter.nfe.NfeNacionalAdapter;
import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.BuscarUmResultado;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.ManifestarResultado;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Roteiro do smoke manual em homologação. Só imprime {@code Desfecho}, NSU e {@code BlobRef}
 * (inv. 3), com o CNPJ mascarado (inv. 13); o cursor local recusa retrocesso (inv. 1).
 */
public final class RoteiroSmoke {

    private final NfeNacionalAdapter adapter;
    private final CursorLocal cursorLocal;
    private final ContextoConsulta contexto;
    private final PrintStream saida;
    private final int limitePaginas;

    public RoteiroSmoke(NfeNacionalAdapter adapter, CursorLocal cursorLocal, ContextoConsulta contexto,
                        PrintStream saida, int limitePaginas) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.cursorLocal = Objects.requireNonNull(cursorLocal, "cursorLocal");
        this.contexto = Objects.requireNonNull(contexto, "contexto");
        this.saida = Objects.requireNonNull(saida, "saida");
        if (limitePaginas < 1) {
            throw new IllegalArgumentException("limitePaginas deve ser >= 1");
        }
        this.limitePaginas = limitePaginas;
    }

    /** Percorre o distNSU a partir do cursor salvo; o bruto já foi gravado pelo adapter (inv. 5). */
    public void distNsu() {
        String cnpj = contexto.cnpj().mascarado();
        for (int pagina = 1; pagina <= limitePaginas; pagina++) {
            Cursor entrada = new Cursor(Long.toString(cursorLocal.ler().valor()));
            BuscarLoteResultado r = adapter.buscarLote(contexto, entrada);
            saida.println("distNSU cnpj=" + cnpj + " pagina=" + pagina + " desfecho=" + nome(r.desfecho())
                    + " ultNSU=" + r.ultNsu().valor() + " documentos=" + r.documentos().size()
                    + " envelope=" + r.envelope().map(b -> mascarar(b.chave())).orElse("-"));
            r.documentos().forEach(d -> saida.println("  nsu=" + d.nsu().valor() + " blob="
                    + d.blobRef().map(b -> mascarar(b.chave())).orElse("-")));
            if (r.desfecho() instanceof Desfecho.Vazio) {
                saida.println("Vazio: pare por ~1h antes de consultar este CNPJ de novo");
                return;
            }
            if (!(r.desfecho() instanceof Desfecho.Ok)) {
                saida.println("parando: desfecho " + nome(r.desfecho()));
                return;
            }
            cursorLocal.gravar(r.ultNsu());
            if (!r.temMais()) {
                saida.println("fim: sem mais documentos");
                return;
            }
        }
        saida.println("parando: limite de páginas (" + limitePaginas + ") atingido");
    }

    public void consultarChave(String chave) {
        BuscarUmResultado r = adapter.buscarPorChave(contexto, new ChaveAcessoNfe(chave));
        saida.println("consChNFe desfecho=" + nome(r.desfecho()) + " blob="
                + r.documento().flatMap(d -> d.blobRef()).map(b -> mascarar(b.chave())).orElse("-"));
    }

    public void manifestarCiencia(String chave, boolean habilitado) {
        if (!habilitado) {
            saida.println("manifestação pulado (FISCAL_SMOKE_MANIFESTAR != true)");
            return;
        }
        var r = adapter.manifestar(contexto, chave, TipoManifestacao.CIENCIA);
        saida.println("manifestar ciencia desfecho="
                + r.map(ManifestarResultado::desfecho).map(RoteiroSmoke::nome).orElse("sem capacidade"));
    }

    /** A chave do blob traz o CNPJ completo; no console sai mascarado (inv. 13). */
    private String mascarar(String chave) {
        return chave.replace(contexto.cnpj().valor(), contexto.cnpj().mascarado());
    }

    private static String nome(Desfecho d) {
        return d.getClass().getSimpleName();
    }
}
