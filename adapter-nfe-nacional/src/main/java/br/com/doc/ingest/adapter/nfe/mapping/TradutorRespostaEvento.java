package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento;
import br.com.doc.ingest.adapter.nfe.evento.LeitorRetEnvEvento.RetornoEvento;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte;
import br.com.doc.ingest.domain.adapter.Desfecho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Único ponto em que transporte, status HTTP e leitura do {@code retEnvEvento} viram {@link Desfecho}.
 *
 * <ul>
 *   <li>transporte e HTTP: mesmas regras do {@link TradutorRespostaDistDfe}</li>
 *   <li>leitura {@code Invalida} (malformado, SOAP Fault, DOCTYPE) → {@code IndisponivelTemporario(5 min)}</li>
 *   <li>lote diferente de {@code 128} → {@link CStatEventoMapper#mapearLote}, ignorando o {@code retEvento}</li>
 *   <li>lote {@code 128} sem {@code retEvento}, ou {@code retEvento} de outro evento → {@code IndisponivelTemporario(5 min)}</li>
 *   <li>lote {@code 128} → {@link CStatEventoMapper#mapearEvento}; {@code Ok} exige chave, tipo e sequência iguais aos enviados</li>
 * </ul>
 *
 * <p>Rejeição pode vir sem identificação; sucesso não, porque {@code Ok} afirma que a manifestação daquela nota existe.
 * Os motivos são textos fixos: nenhum desfecho ou log carrega corpo HTTP, XML ou {@code xMotivo} (inv. 4 e 13).
 */
public final class TradutorRespostaEvento {

    private static final Logger LOG = LoggerFactory.getLogger(TradutorRespostaEvento.class);

    private static final Duration RETRY_TEMP = Duration.ofMinutes(5);

    private final CStatEventoMapper mapper;

    /** O que foi enviado, para conferir que a resposta é deste evento. */
    public record EventoEnviado(String chaveAcesso, String tpEvento, int nSeqEvento) {
        @Override
        public String toString() {
            return "EventoEnviado[tpEvento=" + tpEvento + ", nSeqEvento=" + nSeqEvento + "]";
        }
    }

    public TradutorRespostaEvento(CStatEventoMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Desfecho decidido só pelo transporte/HTTP, ou vazio quando o corpo (2xx) deve ser lido. */
    public Optional<Desfecho> deTransporte(ResultadoTransporte resultado) {
        return switch (resultado) {
            case ResultadoTransporte.FalhaTransporte f -> Optional.of(temporario());
            case ResultadoTransporte.RespostaHttp r -> deStatus(r.status());
        };
    }

    public Desfecho deLeitura(LeitorRetEnvEvento.Resultado leitura, EventoEnviado enviado) {
        return switch (leitura) {
            case LeitorRetEnvEvento.Invalida i -> temporario();
            case LeitorRetEnvEvento.Valida v -> mapper.mapearLote(v.cStatLote())
                    .orElseGet(() -> v.retornoEvento().map(r -> deRetorno(r, enviado)).orElseGet(TradutorRespostaEvento::semRetEvento));
        };
    }

    private Desfecho deRetorno(RetornoEvento retorno, EventoEnviado enviado) {
        if (diverge(retorno.chaveAcesso(), enviado.chaveAcesso())
                || diverge(retorno.tpEvento(), enviado.tpEvento())
                || diverge(retorno.nSeqEvento(), String.valueOf(enviado.nSeqEvento()))) {
            LOG.warn("retEvento nao corresponde ao evento enviado");
            return temporario();
        }
        Desfecho desfecho = mapper.mapearEvento(retorno.cStat());
        boolean identificado = retorno.chaveAcesso().isPresent()
                && retorno.tpEvento().isPresent()
                && retorno.nSeqEvento().isPresent();
        if (desfecho instanceof Desfecho.Ok && !identificado) {
            LOG.warn("retEvento de sucesso sem chave, tipo ou sequencia");
            return temporario();
        }
        return desfecho;
    }

    private static boolean diverge(Optional<String> recebido, String enviado) {
        return recebido.filter(valor -> !valor.equals(enviado)).isPresent();
    }

    private static Desfecho semRetEvento() {
        LOG.warn("lote de evento processado sem retEvento");
        return temporario();
    }

    private static Optional<Desfecho> deStatus(int status) {
        if (status >= 200 && status < 300) {
            return Optional.empty();
        }
        if (status == 401 || status == 403) {
            return Optional.of(new Desfecho.ErroAutenticacao("HTTP " + status + " no envio do evento"));
        }
        if (status == 408 || status == 429) {
            return Optional.of(temporario());
        }
        if (status >= 400 && status < 500) {
            return Optional.of(new Desfecho.IndisponivelDefinitivo("HTTP " + status + " no envio do evento"));
        }
        return Optional.of(temporario());
    }

    private static Desfecho temporario() {
        return new Desfecho.IndisponivelTemporario(RETRY_TEMP);
    }
}
