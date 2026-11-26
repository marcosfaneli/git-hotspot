package com.mfaneli.discovery;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import com.mfaneli.architecture.ArchitectureGraph.Node;

public final class HttpInventory {
    private static final Pattern KEY=Pattern.compile("^spring\\.cloud\\.openfeign\\.client\\.config\\.([A-Za-z0-9_-]+)\\.(connectTimeout|readTimeout|retryer)$");
    public List<Node> scan(Path root) throws IOException {
        var base=root.toAbsolutePath().normalize();
        var result=new ArrayList<Node>();
        try(var walk=Files.walk(base)){
            for(var p:walk.filter(x->Files.isRegularFile(x,LinkOption.NOFOLLOW_LINKS)).filter(x->x.getFileName().toString().matches("application(?:-[A-Za-z0-9_-]+)?\\.properties")).sorted().toList()){
                var relative=base.relativize(p);
                boolean excluded=false;
                for(var part:relative)if(Set.of(".git","target","build","test").contains(part.toString()))excluded=true;
                if(excluded)continue;
                var file=relative.toString().replace('\\','/');
                var lines=Files.readAllLines(p);
                for(int i=0;i<lines.size();i++){
                    var line=lines.get(i).strip();
                    int eq=line.indexOf('=');
                    if(eq<0)continue;
                    var m=KEY.matcher(line.substring(0,eq).strip());
                    if(!m.matches())continue;
                    var value=line.substring(eq+1).strip();
                    var kind="HTTP_CONFIG_UNRESOLVED";
                    if(m.group(2).equals("retryer"))kind="HTTP_RETRY_DECLARED";
                    else if(value.matches("[0-9]+")){
                        try{kind=Long.parseLong(value)==0?"HTTP_TIMEOUT_ZERO":"HTTP_TIMEOUT_DECLARED";}catch(NumberFormatException ignored){}
                    }
                    result.add(new Node("http:"+file+":"+(i+1),kind,m.group(1)+"."+m.group(2),file,i+1));
                }
            }
        }
        return List.copyOf(result);
    }
}
