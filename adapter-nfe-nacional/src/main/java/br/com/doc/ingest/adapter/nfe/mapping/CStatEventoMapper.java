package br.com.doc.ingest.adapter.nfe.mapping;

import br.com.doc.ingest.domain.adapter.Desfecho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Traduz o cStat do retorno do NFeRecepcaoEvento (decisão D1 da E2-S13) para {@link Desfecho}.
 *
 * <p>Separado do {@link CStatMapper} da distribuição: aqui há dois níveis, e {@code 135}/{@code 573} não existem lá.
 * Os códigos saíram de D1 e de fontes secundárias, sem a tabela do MOC aberta: [VALIDAR FONTES-OFICIAIS item 6, smoke E2-S12].
 *
 * <p>cStat desconhecido/nulo/vazio -> {@link Desfecho.IndisponivelTemporario} de 5 min com WARN só do código
 * sanitizado (inv. 13), nos dois níveis. Os motivos são fixos: nada do {@code xMotivo} entra no desfecho.
 */
public class CStatEventoMapper {

    private static final Logger LOG = LoggerFactory.getLogger(CStatEventoMapper.class);

    private static final Duration RETRY_LIMITE = Duration.ofHours(1);
    private static final Duration RETRY_TEMP = Duration.ofMinutes(5);

    /**
     * Mapeia o cStat do {@code retEnvEvento}.
     *
     * @return vazio só para {@code 128} (lote processado): quem decide é o {@code retEvento}
     */
    public Optional<Desfecho> mapearLote(String cStat) {
        if (cStat == null) {
            return Optional.of(fallback(null));
        }
        return switch (cStat) {
            case "128" -> Optional.empty();
            case "108" -> Optional.of(new Desfecho.IndisponivelTemporario(RETRY_TEMP));
            case "109" -> Optional.of(new Desfecho.ServicoFora("Serviço paralisado sem previsão (cStat 109)"));
            case "656" -> Optional.of(new Desfecho.LimiteAtingido(RETRY_LIMITE));
            case "215", "225", "243", "252" ->
                    Optional.of(new Desfecho.IndisponivelDefinitivo("Lote de evento rejeitado (cStat " + cStat + ")"));
            default -> Optional.of(fallback(cStat));
        };
    }

    /** Mapeia o cStat do {@code retEvento/infEvento}. Só {@code 135}, {@code 136} e {@code 573} dão {@code Ok}. */
    public Desfecho mapearEvento(String cStat) {
        if (cStat == null) {
            return fallback(null);
        }
        return switch (cStat) {
            // 573: a ciência já existe, o que torna o envio idempotente
            case "135", "136", "573" -> new Desfecho.Ok();

            // Situação da nota, prazo ou chave: não adianta retentar
            case "650", "655", "596", "217", "236", "614", "615", "616", "617", "618", "619" ->
                    new Desfecho.IndisponivelDefinitivo("Evento rejeitado (cStat " + cStat + ")");

            // 575: o certificado é válido, só não é o do destinatário desta nota; não pode parar a coleta do CNPJ
            case "575" -> new Desfecho.IndisponivelDefinitivo("Autor do evento diverge do destinatário (cStat 575)");

            case "213" -> new Desfecho.ErroAutenticacao("Autor do evento diverge do certificado (cStat 213)");

            case "297", "298" -> {
                LOG.warn("Assinatura do evento rejeitada: [{}]", cStat);
                yield new Desfecho.IndisponivelDefinitivo("Assinatura do evento rejeitada (cStat " + cStat + ")");
            }

            default -> fallback(cStat);
        };
    }

    private Desfecho fallback(String cStat) {
        LOG.warn("cStat de evento nao mapeado, usando fallback IndisponivelTemporario: [{}]", CStatMapper.sanitizar(cStat));
        return new Desfecho.IndisponivelTemporario(RETRY_TEMP);
    }
}
