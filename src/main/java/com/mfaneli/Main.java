package com.mfaneli;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && java.util.Set.of("review","trace").contains(args[0])) {
            if (args.length != 4) {
                System.err.println("Usage: review <service-directory> <service-id> <owners.txt> | trace <directory> <from-id> <to-id>");
                System.exit(2);
            }
            try {
                var root = java.nio.file.Path.of(args[1]);
                if (args[0].equals("review")) {
                    var engine = new com.mfaneli.architecture.RuleEngine();
                    var evidence = new com.mfaneli.discovery.AccessInventory().scan(root);
                    var findings = engine.review(args[2],evidence,engine.catalog(java.nio.file.Path.of(args[3])));
                    for (var finding : findings) System.out.println(finding);
                    System.out.println("Findings: "+findings.size());
                    System.out.println("Unresolved SQL/JPQL: "+evidence.stream().filter(n -> n.kind().startsWith("UNRESOLVED_")).count());
                } else {
                    var graph = new com.mfaneli.architecture.GraphDiscovery().scan(root);
                    var path = new com.mfaneli.architecture.GraphQueries().path(graph,args[2],args[3]);
                    System.out.println(path.isEmpty() ? "No candidate path found" : "Possible path: "+String.join(" -> ",path));
                }
            } catch (IOException | IllegalArgumentException e) { System.err.println(e.getMessage()); System.exit(1); }
            return;
        }
        if (args.length > 0 && args[0].equals("graph")) {
            if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--json"))) {
                System.err.println("Usage: graph <directory> [--json]");
                System.exit(2);
            }
            try {
                var graph = new com.mfaneli.architecture.GraphDiscovery().scan(java.nio.file.Path.of(args[1]));
                System.out.print(args.length == 3 ? com.mfaneli.architecture.GraphReport.json(graph) + "\n" : com.mfaneli.architecture.GraphReport.text(graph));
            } catch (IOException | IllegalArgumentException e) { System.err.println(e.getMessage()); System.exit(1); }
            return;
        }
        if (args.length > 0 && args[0].equals("scan")) {
            if (args.length != 2) { System.err.println("Usage: scan <directory>"); System.exit(2); }
            try {
                for (var entry : new com.mfaneli.discovery.SourceInventory().scan(java.nio.file.Path.of(args[1]))) {
                    System.out.printf("%s | %s | %s:%d%n", entry.kind(), entry.name(), entry.file(), entry.line());
                }
            } catch (IOException e) { System.err.println(e.getMessage()); System.exit(1); }
            return;
        }
        if (args.length > 0 && args[0].equals("hotspot")) args = java.util.Arrays.copyOfRange(args, 1, args.length);
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
