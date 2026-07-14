package com.mfaneli;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            CliArguments cli = new CliParser().parse(args);
            List<RepositoryHotspotResult> results = new GitHotspotAnalyzer()
                    .analyze(cli.repositories());

            new HtmlReportWriter().write(results, cli.outputDirectory());
            System.out.println("Relatorio gerado em: " + cli.outputDirectory().resolve("index.html"));
        } catch (CliException | IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
