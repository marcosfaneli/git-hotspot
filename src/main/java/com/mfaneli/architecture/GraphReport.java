package com.mfaneli.architecture;

public final class GraphReport {
    private GraphReport() {}
    public static String json(ArchitectureGraph graph) {
        var nodes = graph.nodes().stream().map(n -> "{\"id\":"+q(n.id())+",\"kind\":"+q(n.kind())+",\"name\":"+q(n.name())+",\"file\":"+q(n.file())+",\"line\":"+n.line()+"}").toList();
        var edges = graph.edges().stream().map(e -> "{\"from\":"+q(e.from())+",\"to\":"+q(e.to())+",\"kind\":"+q(e.kind())+"}").toList();
        return "{\"schemaVersion\":1,\"nodes\":["+String.join(",",nodes)+"],\"edges\":["+String.join(",",edges)+"]}";
    }
    public static String text(ArchitectureGraph graph) {
        var out = new StringBuilder();
        for (var n : graph.nodes()) out.append(n.kind()).append(" | ").append(n.name()).append(" | ").append(n.file()).append(':').append(n.line()).append('\n');
        for (var e : graph.edges()) out.append(e.kind()).append(" | ").append(e.from()).append(" -> ").append(e.to()).append('\n');
        return out.toString();
    }
    static String q(String s) {
        var out = new StringBuilder("\"");
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> { if(c<32) out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
