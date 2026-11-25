package com.mfaneli.architecture;

import com.mfaneli.discovery.SourceInventory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import static com.mfaneli.architecture.ArchitectureGraph.*;

public final class GraphDiscovery {
    public ArchitectureGraph scan(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        final Path scanRoot = root;
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        nodes.add(new Node("root", "SCAN_ROOT", ".", ".", 0));
        var modules = new TreeMap<String,String>();
        try (var paths = Files.walk(root)) {
            for (var pom : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(p -> !excluded(scanRoot.relativize(p))).sorted().toList()) {
                var file = relative(root, pom);
                var dir = relative(root, pom.getParent());
                var project = parse(pom);
                var artifact = child(project, "artifactId");
                if (artifact.isBlank()) throw new IOException("Missing artifactId: " + file);
                var id = "module:" + file;
                modules.put(dir, id);
                nodes.add(new Node(id, "MAVEN_PROJECT", artifact, file, 0));
                edges.add(new Edge("root", id, "CONTAINS"));
                var dependencies = element(project, "dependencies");
                if (dependencies != null) {
                    for (var n = dependencies.getFirstChild(); n != null; n = n.getNextSibling()) {
                        if (!(n instanceof Element d) || !d.getTagName().equals("dependency")) continue;
                        var coordinate = child(d,"groupId") + ":" + child(d,"artifactId") + ":" + child(d,"version");
                        var dep = "dependency:" + id + ":" + coordinate;
                        nodes.add(new Node(dep,"DECLARED_DEPENDENCY",coordinate,file,0));
                        edges.add(new Edge(id,dep,"DECLARES_DEPENDENCY"));
                    }
                }
            }
        }
        var files = new HashSet<String>();
        for (var entry : new SourceInventory().scan(root)) {
            var fileId = "file:" + entry.file();
            if (files.add(fileId)) {
                nodes.add(new Node(fileId,"SOURCE_FILE",entry.file(),entry.file(),0));
                var parent = modules.entrySet().stream()
                    .filter(e -> e.getKey().isEmpty() || entry.file().startsWith(e.getKey()+"/"))
                    .max(Comparator.comparingInt(e -> e.getKey().length())).map(Map.Entry::getValue).orElse("root");
                edges.add(new Edge(parent,fileId,"CONTAINS"));
            }
            var id = fileId + ":" + entry.kind() + ":" + entry.name() + ":" + entry.line();
            nodes.add(new Node(id,entry.kind(),entry.name(),entry.file(),entry.line()));
            edges.add(new Edge(fileId,id,"CONTAINS"));
        }
        var callGraph = new com.mfaneli.discovery.CallInventory().scan(root);
        nodes.addAll(callGraph.nodes());
        edges.addAll(callGraph.edges());
        for (var node : callGraph.nodes()) {
            if (node.kind().equals("JAVA_TYPE"))
                edges.add(new Edge("file:"+node.file(),node.id(),"CONTAINS"));
        }
        for (var evidence : new com.mfaneli.discovery.AccessInventory().scan(root)) {
            nodes.add(evidence);
            String fileId = "file:"+evidence.file();
            if (files.add(fileId)) {
                nodes.add(new Node(fileId,"SOURCE_FILE",evidence.file(),evidence.file(),0));
                edges.add(new Edge("root",fileId,"CONTAINS"));
            }
            edges.add(new Edge(fileId,evidence.id(),"CONTAINS"));
        }
        return new ArchitectureGraph(nodes,edges);
    }
    private static String relative(Path root, Path p) { return root.relativize(p).toString().replace('\\','/'); }
    private static boolean excluded(Path p) {
        for (var part : p) if (Set.of(".git","target","build","test").contains(part.toString())) return true;
        return false;
    }
    private static Element parse(Path path) throws IOException {
        try {
            var f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");
            f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
        } catch (ParserConfigurationException | SAXException e) { throw new IOException("Invalid POM: " + path,e); }
    }
    private static Element element(Element parent,String name) {
        for (var n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && e.getTagName().equals(name)) return e;
        return null;
    }
    private static String child(Element parent,String name) {
        var e = element(parent,name); return e == null ? "" : e.getTextContent().trim();
    }
}
