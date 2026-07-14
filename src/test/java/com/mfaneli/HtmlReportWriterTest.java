package com.mfaneli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlReportWriterTest {

    @TempDir
    Path outputDir;

    private static RepositoryHotspotResult repositorio(String nome, Map<String, Integer> alteracoes) {
        return new RepositoryHotspotResult(
                new File(nome),
                new GitHotspotResult(alteracoes.size(), alteracoes)
        );
    }

    @Test
    void write_criaIndexHtml() throws Exception {
        new HtmlReportWriter().write(List.of(repositorio("repo", Map.of("A.java", 1))), outputDir);

        assertTrue(Files.exists(outputDir.resolve("index.html")));
    }

    @Test
    void write_criaDiretorioRepositories() throws Exception {
        new HtmlReportWriter().write(List.of(repositorio("repo", Map.of("A.java", 1))), outputDir);

        assertTrue(Files.isDirectory(outputDir.resolve("repositories")));
    }

    @Test
    void write_criaArquivoDeDetalhe() throws Exception {
        new HtmlReportWriter().write(List.of(repositorio("repo", Map.of("A.java", 1))), outputDir);

        assertTrue(Files.exists(outputDir.resolve("repositories/repository-1.html")));
    }

    @Test
    void write_indexContemNomeDoRepositorio() throws Exception {
        new HtmlReportWriter().write(List.of(repositorio("meu-projeto", Map.of("A.java", 1))), outputDir);

        String conteudo = Files.readString(outputDir.resolve("index.html"));
        assertTrue(conteudo.contains("meu-projeto"));
    }

    @Test
    void write_detalheContemArquivoAlterado() throws Exception {
        new HtmlReportWriter().write(List.of(repositorio("repo", Map.of("Hotspot.java", 5))), outputDir);

        String conteudo = Files.readString(outputDir.resolve("repositories/repository-1.html"));
        assertTrue(conteudo.contains("Hotspot.java"));
    }

    @Test
    void write_detalheOrdenadoPorAlteracoesDecrescente() throws Exception {
        Map<String, Integer> alteracoes = Map.of("raro.java", 1, "frequente.java", 10);
        new HtmlReportWriter().write(List.of(repositorio("repo", alteracoes)), outputDir);

        String conteudo = Files.readString(outputDir.resolve("repositories/repository-1.html"));
        assertTrue(conteudo.indexOf("frequente.java") < conteudo.indexOf("raro.java"));
    }

    @Test
    void write_multiplosRepositorios_criaMultiplosDetalhes() throws Exception {
        List<RepositoryHotspotResult> repos = List.of(
                repositorio("repo-a", Map.of("A.java", 1)),
                repositorio("repo-b", Map.of("B.java", 2))
        );
        new HtmlReportWriter().write(repos, outputDir);

        assertTrue(Files.exists(outputDir.resolve("repositories/repository-1.html")));
        assertTrue(Files.exists(outputDir.resolve("repositories/repository-2.html")));
    }
}
