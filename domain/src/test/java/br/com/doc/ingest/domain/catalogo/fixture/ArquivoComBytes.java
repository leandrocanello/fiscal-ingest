package br.com.doc.ingest.domain.catalogo.fixture;

/** Fixture do meta-teste: classe do catálogo que carrega bytes e deve ser recusada pela regra. */
public record ArquivoComBytes(byte[] conteudo) {
}
