package com.mfaneli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliParserTest {

    private final CliParser parser = new CliParser();

    @Test
    void parse_repositorioUnico() {
        CliArguments result = parser.parse(new String[]{"/tmp/repo"});

        assertEquals(1, result.repositories().size());
        assertEquals(Path.of("/tmp/repo").toAbsolutePath().normalize(),
                result.repositories().get(0).toPath());
    }

    @Test
    void parse_multiplosRepositorios() {
        CliArguments result = parser.parse(new String[]{"/tmp/repo1", "/tmp/repo2"});

        assertEquals(2, result.repositories().size());
    }

    @Test
    void parse_outputComFlagLonga() {
        CliArguments result = parser.parse(new String[]{"--output", "/tmp/saida", "/tmp/repo"});

        assertEquals(Path.of("/tmp/saida").toAbsolutePath().normalize(), result.outputDirectory());
    }

    @Test
    void parse_outputComFlagCurta() {
        CliArguments result = parser.parse(new String[]{"-o", "/tmp/saida", "/tmp/repo"});

        assertEquals(Path.of("/tmp/saida").toAbsolutePath().normalize(), result.outputDirectory());
    }

    @Test
    void parse_semRepositorios_lancaCliException() {
        assertThrows(CliException.class, () -> parser.parse(new String[]{}));
    }

    @Test
    void parse_outputSemValor_lancaCliException() {
        assertThrows(CliException.class, () -> parser.parse(new String[]{"--output"}));
    }

    @Test
    void parse_mensagemDeErroContemUso() {
        CliException ex = assertThrows(CliException.class, () -> parser.parse(new String[]{}));

        assertTrue(ex.getMessage().contains("Uso:"));
    }
}
