package com.mfaneli;

class CliException extends RuntimeException {
    private static final String USO = "Uso: java com.mfaneli.Main [--output pasta-do-relatorio] "
            + "<repositorio-1> [repositorio-2 ...]";

    CliException(String message) {
        super(message + System.lineSeparator() + USO);
    }
}
