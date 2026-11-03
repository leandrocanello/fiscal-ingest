package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.adapter.Credencial;

import java.time.Instant;

/**
 * Desfecho de {@link CofreDeCertificados#abrir}. Fora de {@link Aberto}, nada foi decifrado
 * para quem chamou.
 */
public sealed interface ResultadoAbertura {

    /** Credencial só em memória; quem recebe deve chamar {@link Credencial#limpar()} depois do uso. */
    record Aberto(Credencial credencial) implements ResultadoAbertura { }

    /** Id inexistente ou de outro tenant: os dois casos não se distinguem. */
    record NaoEncontrado() implements ResultadoAbertura { }

    /** O certificado venceu; o envelope nem chega a ser lido. */
    record Vencido(Instant validoAte) implements ResultadoAbertura { }

    /** O certificado existe, mas não pôde ser decifrado. */
    record Indisponivel(FalhaAbertura falha) implements ResultadoAbertura { }
}
