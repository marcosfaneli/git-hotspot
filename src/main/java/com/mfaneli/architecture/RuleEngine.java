package com.mfaneli.architecture;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.mfaneli.architecture.ArchitectureGraph.*;

public final class RuleEngine {
    public record Finding(String rule,String status,String service,String table,String owner,String file,long line) {}
    public Map<String,String> catalog(Path file) throws IOException {
        var owners=new TreeMap<String,String>();
        for(var line:Files.readAllLines(file)) {
            if(line.isBlank()||line.stripLeading().startsWith("#")) continue;
            var parts=line.split("=",2);
            if(parts.length!=2||parts[0].isBlank()||parts[1].isBlank()) throw new IOException("Invalid ownership entry");
            String key=parts[0].strip().toUpperCase(Locale.ROOT);
            if(owners.putIfAbsent(key,parts[1].strip())!=null) throw new IOException("Duplicate table: "+key);
        }
        return Map.copyOf(owners);
    }
    public List<Finding> review(String service,List<Node> evidence,Map<String,String> owners) {
        var findings=new ArrayList<Finding>();
        for(var node:evidence) {
            if(!Set.of("SQL_READ_CANDIDATE","SQL_WRITE_CANDIDATE").contains(node.kind())) continue;
            String owner=owners.get(node.name());
            if(owner==null) findings.add(new Finding("UNKNOWN_OWNERSHIP","REVIEW",service,node.name(),"",node.file(),node.line()));
            else if(!owner.equals(service)) findings.add(new Finding(node.kind().equals("SQL_WRITE_CANDIDATE")?"CROSS_OWNER_WRITE":"CROSS_OWNER_READ","CANDIDATE",service,node.name(),owner,node.file(),node.line()));
        }
        return findings.stream().distinct().sorted(Comparator.comparing(Finding::rule).thenComparing(Finding::file).thenComparingLong(Finding::line).thenComparing(Finding::table)).toList();
    }
}
