package com.mfaneli;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class CliParser {
    private static final Path DEFAULT_OUTPUT_DIRECTORY = Path.of("git-hotspot-report");

    CliArguments parse(String[] args) {
        List<File> repositories = new ArrayList<>();
        Path outputDirectory = DEFAULT_OUTPUT_DIRECTORY.toAbsolutePath();

        for (int index = 0; index < args.length; index++) {
            if ("--output".equals(args[index]) || "-o".equals(args[index])) {
                if (++index >= args.length) {
                    throw new CliException("Informe uma pasta depois de " + args[index - 1] + ".");
                }
                outputDirectory = Path.of(args[index]).toAbsolutePath().normalize();
            } else {
                repositories.add(Path.of(args[index]).toAbsolutePath().normalize().toFile());
            }
        }

        if (repositories.isEmpty()) {
            throw new CliException("Informe ao menos um repositorio.");
        }

        return new CliArguments(List.copyOf(repositories), outputDirectory);
    }
}
