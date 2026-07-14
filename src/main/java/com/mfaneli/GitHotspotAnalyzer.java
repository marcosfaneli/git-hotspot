package com.mfaneli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitHotspotAnalyzer {

    public List<RepositoryHotspotResult> analyze(List<File> repositories) throws IOException {
        List<RepositoryHotspotResult> results = new ArrayList<>();

        for (File repository : repositories) {
            validateRepository(repository);
            results.add(new RepositoryHotspotResult(repository, analyze(repository)));
        }

        return List.copyOf(results);
    }

    public GitHotspotResult analyze(File repository) throws IOException {
        List<String> commitIds = executeGit(repository, "rev-list", "HEAD");
        Map<String, Integer> fileChanges = new HashMap<>();

        for (String commitId : commitIds) {
            List<String> changedFiles = executeGit(
                    repository,
                    "diff-tree",
                    "--no-commit-id",
                    "--name-only",
                    "-r",
                    commitId
            );

            for (String file : changedFiles) {
                fileChanges.merge(file, 1, Integer::sum);
            }
        }

        return new GitHotspotResult(commitIds.size(), fileChanges);
    }

    private void validateRepository(File repository) {
        if (!repository.isDirectory()) {
            throw new IllegalArgumentException(
                    "O caminho informado nao e um diretorio: " + repository
            );
        }
    }

    private List<String> executeGit(File repository, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));

        Process process = new ProcessBuilder(command)
                .directory(repository)
                .redirectErrorStream(true)
                .start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    output.add(line);
                }
            }
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("A execucao do Git foi interrompida", e);
        }

        if (exitCode != 0) {
            throw new IOException("Erro ao executar Git: " + String.join(System.lineSeparator(), output));
        }

        return output;
    }
}
