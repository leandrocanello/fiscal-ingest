package br.com.doc.ingest.domain.usecase;

/** O que aconteceu com uma {@code ChaveColeta} na rotação do certificado. */
public sealed interface DesfechoRotacao {

    /** O {@code certificado_id} passou para o vigente. */
    record Trocada() implements DesfechoRotacao { }

    /** A chave já usava o certificado vigente. */
    record SemMudanca() implements DesfechoRotacao { }

    /** Outra rotação passou antes, mesmo depois de reler uma vez; nada foi gravado. */
    record Conflito(Long atual) implements DesfechoRotacao { }

    /** A chave aponta para certificado de outro tenant (DA-54); nada foi gravado. */
    record OutroTenant(long certificadoAtualId) implements DesfechoRotacao { }

    /** A chave deixou de existir entre a listagem e a troca. */
    record NaoEncontrada() implements DesfechoRotacao { }
}
