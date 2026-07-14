package com.mfaneli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class HtmlReportWriter {
    private static final String INDEX_FILE_NAME = "index.html";
    private static final String REPOSITORIES_DIRECTORY_NAME = "repositories";

    public void write(List<RepositoryHotspotResult> repositories, Path outputDirectory) throws IOException {
        Path repositoriesDirectory = outputDirectory.resolve(REPOSITORIES_DIRECTORY_NAME);
        Files.createDirectories(repositoriesDirectory);

        List<String> detailFiles = detailFileNames(repositories.size());
        List<String> detailLinks = detailFiles.stream()
                .map(fileName -> REPOSITORIES_DIRECTORY_NAME + "/" + fileName)
                .toList();
        Files.writeString(
                outputDirectory.resolve(INDEX_FILE_NAME),
                buildIndex(repositories, detailLinks),
                StandardCharsets.UTF_8
        );

        for (int index = 0; index < repositories.size(); index++) {
            Path detailOutput = repositoriesDirectory.resolve(detailFiles.get(index));
            Files.writeString(
                    detailOutput,
                    buildRepositoryPage(repositories.get(index)),
                    StandardCharsets.UTF_8
            );
        }
    }

    private List<String> detailFileNames(int repositoryCount) {
        List<String> names = new ArrayList<>();

        for (int index = 1; index <= repositoryCount; index++) {
            names.add("repository-" + index + ".html");
        }
        return names;
    }

    private String buildIndex(
            List<RepositoryHotspotResult> repositories,
            List<String> detailFiles
    ) {
        int totalCommits = repositories.stream()
                .mapToInt(repository -> repository.hotspot().commitCount())
                .sum();
        int totalFiles = repositories.stream()
                .mapToInt(repository -> repository.hotspot().fileChanges().size())
                .sum();

        StringBuilder html = new StringBuilder();
        appendDocumentStart(html, "Git Hotspot", true);
        html.append("""
                  <header class="hero">
                    <span class="eyebrow">Relatório consolidado</span>
                    <h1>Git Hotspot</h1>
                    <p>Selecione um repositório para consultar seus arquivos mais alterados.</p>
                  </header>
                  <section class="summary" aria-label="Resumo geral">
                """);
        appendCard(html, "Repositórios", repositories.size());
        appendCard(html, "Commits analisados", totalCommits);
        appendCard(html, "Arquivos encontrados", totalFiles);
        html.append("</section><section class=\"repository-grid\" aria-label=\"Repositórios\">");

        for (int index = 0; index < repositories.size(); index++) {
            RepositoryHotspotResult repository = repositories.get(index);
            html.append("<a class=\"repository-card\" href=\"")
                    .append(escapeHtml(detailFiles.get(index)))
                    .append("\"><div><span class=\"repository-name\">")
                    .append(escapeHtml(repository.repository().getName()))
                    .append("</span><span class=\"path\">")
                    .append(escapeHtml(repository.repository().getAbsolutePath()))
                    .append("</span></div><div class=\"repository-stats\"><span>")
                    .append(repository.hotspot().commitCount())
                    .append(" commits</span><span>")
                    .append(repository.hotspot().fileChanges().size())
                    .append(" arquivos</span></div><span class=\"arrow\" aria-hidden=\"true\">→</span></a>");
        }

        html.append("</section>");
        appendDocumentEnd(html);
        return html.toString();
    }

    private String buildRepositoryPage(RepositoryHotspotResult repository) {
        List<Map.Entry<String, Integer>> files = sortedFiles(repository.hotspot());
        int maximumChanges = files.stream().mapToInt(Map.Entry::getValue).max().orElse(0);

        StringBuilder html = new StringBuilder();
        appendDocumentStart(html, repository.repository().getName() + " · Git Hotspot", false);
        html.append("<a class=\"back-link\" href=\"../index.html\">← Voltar para os repositórios</a><header class=\"detail-header\"><span class=\"eyebrow\">Detalhes do repositório</span><h1>")
                .append(escapeHtml(repository.repository().getName()))
                .append("</h1><p class=\"path\">")
                .append(escapeHtml(repository.repository().getAbsolutePath()))
                .append("</p></header><section class=\"summary\" aria-label=\"Resumo do repositório\">");
        appendCard(html, "Commits analisados", repository.hotspot().commitCount());
        appendCard(html, "Arquivos alterados", files.size());
        html.append("</section><section class=\"table-wrapper\">");

        if (files.isEmpty()) {
            html.append("<p class=\"empty\">Nenhuma alteração encontrada.</p>");
        } else {
            appendTable(html, files, maximumChanges);
        }

        html.append("</section>");
        appendDocumentEnd(html);
        return html.toString();
    }

    private void appendDocumentStart(StringBuilder html, String title, boolean indexPage) {
        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>")
                .append(escapeHtml(title))
                .append("</title><style>")
                .append(commonStyles());

        if (indexPage) {
            html.append("""
                    .repository-grid { display: grid; gap: 14px; }
                    .repository-card { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) auto 32px; gap: 20px; align-items: center; padding: 22px; border: 1px solid #334155; border-radius: 14px; background: #1e293b; color: #e2e8f0; text-decoration: none; transition: transform .15s ease, border-color .15s ease; }
                    .repository-card:hover { transform: translateY(-2px); border-color: #22d3ee; }
                    .repository-name { display: block; margin-bottom: 7px; font-size: 1.15rem; font-weight: 700; }
                    .repository-stats { display: flex; gap: 8px; color: #cbd5e1; white-space: nowrap; }
                    .repository-stats span { padding: 6px 10px; border-radius: 999px; background: #334155; }
                    .arrow { color: #22d3ee; font-size: 1.5rem; }
                    @media (max-width: 700px) { .repository-card { grid-template-columns: 1fr 24px; } .repository-stats { grid-column: 1 / -1; grid-row: 2; flex-wrap: wrap; } .arrow { grid-column: 2; grid-row: 1; } }
                    """);
        }

        html.append("</style></head><body><main>");
    }

    private String commonStyles() {
        return """
                :root { color-scheme: dark; font-family: Inter, system-ui, sans-serif; }
                * { box-sizing: border-box; }
                body { margin: 0; background: #0f172a; color: #e2e8f0; }
                main { width: min(1100px, calc(100% - 32px)); margin: 48px auto; }
                h1 { margin: 6px 0 10px; font-size: clamp(2rem, 5vw, 3.5rem); overflow-wrap: anywhere; }
                .hero, .detail-header { margin-bottom: 30px; }
                .hero p, .path { color: #94a3b8; overflow-wrap: anywhere; }
                .eyebrow { color: #22d3ee; font-size: .78rem; font-weight: 700; text-transform: uppercase; letter-spacing: .12em; }
                .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; margin: 28px 0; }
                .card { padding: 18px; border: 1px solid #334155; border-radius: 14px; background: #1e293b; }
                .label { display: block; color: #94a3b8; font-size: .78rem; text-transform: uppercase; letter-spacing: .08em; }
                .value { display: block; margin-top: 6px; font-size: 1.8rem; font-weight: 700; }
                .back-link { display: inline-block; margin-bottom: 28px; color: #67e8f9; text-decoration: none; }
                .back-link:hover { text-decoration: underline; }
                .table-wrapper { overflow-x: auto; border: 1px solid #334155; border-radius: 14px; background: #1e293b; }
                table { width: 100%; border-collapse: collapse; }
                th, td { padding: 14px 18px; border-bottom: 1px solid #334155; text-align: left; }
                th { color: #94a3b8; font-size: .78rem; text-transform: uppercase; letter-spacing: .06em; }
                tr:last-child td { border-bottom: 0; }
                .file { min-width: 280px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; overflow-wrap: anywhere; }
                .frequency { width: 38%; min-width: 190px; }
                .bar-track { height: 9px; border-radius: 999px; background: #334155; overflow: hidden; }
                .bar { height: 100%; border-radius: inherit; background: linear-gradient(90deg, #22d3ee, #818cf8); }
                .empty { padding: 32px; color: #94a3b8; text-align: center; }
                """;
    }

    private void appendCard(StringBuilder html, String label, int value) {
        html.append("<div class=\"card\"><span class=\"label\">")
                .append(label)
                .append("</span><span class=\"value\">")
                .append(value)
                .append("</span></div>");
    }

    private List<Map.Entry<String, Integer>> sortedFiles(GitHotspotResult result) {
        return result.fileChanges().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
    }

    private void appendTable(
            StringBuilder html,
            List<Map.Entry<String, Integer>> files,
            int maximumChanges
    ) {
        html.append("<table><thead><tr><th>Arquivo</th><th>Alterações</th><th>Frequência</th></tr></thead><tbody>");
        for (Map.Entry<String, Integer> file : files) {
            int percentage = Math.max(1, file.getValue() * 100 / maximumChanges);
            html.append("<tr><td class=\"file\">")
                    .append(escapeHtml(file.getKey()))
                    .append("</td><td>")
                    .append(file.getValue())
                    .append("</td><td class=\"frequency\"><div class=\"bar-track\"><div class=\"bar\" style=\"width:")
                    .append(percentage)
                    .append("%\"></div></div></td></tr>");
        }
        html.append("</tbody></table>");
    }

    private void appendDocumentEnd(StringBuilder html) {
        html.append("</main></body></html>");
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
