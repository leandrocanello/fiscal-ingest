package br.com.doc.ingest.adapter.nfe.transporte;

/** Resultado bruto do transporte: não interpreta status HTTP nem cStat (isso é do adapter). */
public sealed interface ResultadoTransporte {

    record RespostaHttp(int status, byte[] corpo) implements ResultadoTransporte {}

    record FalhaTransporte(Motivo motivo) implements ResultadoTransporte {}

    enum Motivo { TIMEOUT, CONEXAO, RESPOSTA_GRANDE_DEMAIS }
}
