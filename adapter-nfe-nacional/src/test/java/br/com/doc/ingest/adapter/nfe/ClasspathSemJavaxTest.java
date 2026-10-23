package br.com.doc.ingest.adapter.nfe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClasspathSemJavaxTest {

    @ParameterizedTest(name = "{0} não deve estar no classpath")
    @ValueSource(strings = {
            "org.apache.cxf.Bus",
            "javax.xml.ws.Service",
            "javax.jws.WebService",
            "javax.xml.bind.JAXBContext"
    })
    void classeLegadaNaoDeveEstarNoClasspath(String nome) {
        assertThatThrownBy(() -> Class.forName(nome, false, getClass().getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
