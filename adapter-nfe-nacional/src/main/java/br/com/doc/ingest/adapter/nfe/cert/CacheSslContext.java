package br.com.doc.ingest.adapter.nfe.cert;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLContext;

/**
 * Cache LRU + TTL de {@link SSLContext} mTLS, para não refazer a abertura do .pfx a cada consulta (§6.2).
 *
 * <p>A chave é o fingerprint (SHA-256) dos bytes do .pfx, nunca a senha (inv. 13). Um .pfx renovado tem outro
 * fingerprint e gera outro contexto; {@link #invalidar(String)} força a recarga. O TTL conta a partir da
 * criação da entrada, pelo {@link Clock} injetado.
 *
 * <p>Por ora, um lock único serializa {@link #obter(Credencial)}, inclusive a carga.
 */
public class CacheSslContext {

    private final CarregadorPfx carregador;
    private final FabricaSslContext fabrica;
    private final KeyStore confianca;
    private final Clock relogio;
    private final int capacidade;
    private final Duration ttl;
    private final LinkedHashMap<String, Entrada> entradas;

    public CacheSslContext(CarregadorPfx carregador, FabricaSslContext fabrica, KeyStore confianca, Clock relogio,
                           int capacidade, Duration ttl) {
        this.carregador = Objects.requireNonNull(carregador, "carregador");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
        this.confianca = Objects.requireNonNull(confianca, "confianca");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (capacidade < 1) {
            throw new IllegalArgumentException("capacidade deve ser >= 1");
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl deve ser positivo");
        }
        this.capacidade = capacidade;
        this.entradas = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entrada> maisAntiga) {
                return size() > CacheSslContext.this.capacidade;
            }
        };
    }

    public synchronized SSLContext obter(Credencial credencial) {
        Objects.requireNonNull(credencial, "credencial");
        String fingerprint = fingerprint(credencial);
        Instant agora = relogio.instant();
        Entrada entrada = entradas.get(fingerprint);
        if (entrada != null && agora.isBefore(entrada.criadaEm().plus(ttl))) {
            return entrada.contexto();
        }
        SSLContext contexto = fabrica.criar(carregador.carregar(credencial), confianca);
        entradas.put(fingerprint, new Entrada(contexto, agora));
        return contexto;
    }

    public synchronized void invalidar(String fingerprint) {
        entradas.remove(Objects.requireNonNull(fingerprint, "fingerprint"));
    }

    synchronized int tamanho() {
        return entradas.size();
    }

    private static String fingerprint(Credencial credencial) {
        byte[] pfx = credencial.pfx();
        try {
            return Sha256.hex(pfx);
        } finally {
            Arrays.fill(pfx, (byte) 0);
        }
    }

    private record Entrada(SSLContext contexto, Instant criadaEm) {
    }
}
