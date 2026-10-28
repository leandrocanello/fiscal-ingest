package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.adapter.nfe.soap.LeitorRespostaDistDfe;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte;
import br.com.doc.ingest.domain.adapter.Desfecho;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Único ponto em que transporte, status HTTP e leitura do {@code retDistDFeInt} viram {@link Desfecho} (DA-14).
 *
 * <ul>
 *   <li>timeout, conexão, resposta grande demais, 5xx, 408, 429, 3xx → {@code IndisponivelTemporario(5 min)}</li>
 *   <li>401/403 → {@code ErroAutenticacao}; outro 4xx → {@code IndisponivelDefinitivo}</li>
 *   <li>2xx → não decide; o corpo segue para a leitura</li>
 *   <li>leitura {@code Invalida} (malformado, SOAP Fault, DOCTYPE) → {@code IndisponivelTemporario(5 min)}</li>
 *   <li>leitura {@code Valida} → {@link CStatMapper}</li>
 * </ul>
 *
 * <p>Os motivos são textos fixos: nenhum desfecho carrega corpo HTTP, XML ou {@code xMotivo} (inv. 4 e 13).
 */
public final class TradutorRespostaDistDfe {

    private static final Duration RETRY_TEMP = Duration.ofMinutes(5);

    private final CStatMapper mapper;

    public TradutorRespostaDistDfe(CStatMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Desfecho decidido só pelo transporte/HTTP, ou vazio quando o corpo (2xx) deve ser lido. */
    public Optional<Desfecho> deTransporte(ResultadoTransporte resultado) {
        return switch (resultado) {
            case ResultadoTransporte.FalhaTransporte f -> Optional.of(temporario());
            case ResultadoTransporte.RespostaHttp r -> deStatus(r.status());
        };
    }

    public Desfecho deLeitura(LeitorRespostaDistDfe.Resultado leitura) {
        return switch (leitura) {
            case LeitorRespostaDistDfe.Valida v -> mapper.mapear(v.retorno().getCStat());
            case LeitorRespostaDistDfe.Invalida i -> temporario();
        };
    }

    private static Optional<Desfecho> deStatus(int status) {
        if (status >= 200 && status < 300) {
            return Optional.empty();
        }
        if (status == 401 || status == 403) {
            return Optional.of(new Desfecho.ErroAutenticacao("HTTP " + status + " na distribuição"));
        }
        if (status == 408 || status == 429) {
            return Optional.of(temporario());
        }
        if (status >= 400 && status < 500) {
            return Optional.of(new Desfecho.IndisponivelDefinitivo("HTTP " + status + " na distribuição"));
        }
        return Optional.of(temporario());
    }

    private static Desfecho temporario() {
        return new Desfecho.IndisponivelTemporario(RETRY_TEMP);
    }
}
