package com.mfaneli.architecture;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import static com.mfaneli.architecture.PolicyEngine.*;

public record OwnershipPolicy(String operation) implements Rule {
    public record Scope(String service,String database,Map<String,String> owners) {
        public Scope { owners=Map.copyOf(owners); }
        public static Scope load(String service,String database,Path path) throws IOException {
            if(service.isBlank()||database.isBlank()||database.contains(":")) throw new IOException("Invalid service/database");
            return new Scope(service,database.toUpperCase(Locale.ROOT),new RuleEngine().catalog(path));
        }
    }
    public String id(){return "OWN-"+operation;}
    public String category(){return "OWNERSHIP";}
    public Severity severity(){return operation.equals("WRITE")?Severity.ERROR:Severity.WARNING;}
    public List<Finding> evaluate(Context c){
        if(c.ownership()==null) return List.of();
        var scope=c.ownership();
        var result=new ArrayList<Finding>();
        for(var n:c.persistence()){
            if(!Set.of("SQL_READ_CANDIDATE","SQL_WRITE_CANDIDATE","JPA_READ_CANDIDATE","JPA_WRITE_CANDIDATE").contains(n.kind())) continue;
            var key=scope.database()+":"+n.name().toUpperCase(Locale.ROOT);
            var owner=scope.owners().get(key);
            boolean unknown=owner==null;
            if(operation.equals("UNKNOWN")?unknown:!unknown&&!scope.service().equals(owner)&&n.kind().endsWith("_"+operation+"_CANDIDATE"))
                result.add(new Finding(id(),category(),severity(),unknown?"INCONCLUSIVE":"CANDIDATE",n.file(),n.line(),key+" owner="+(unknown?"unknown":owner)+" service="+scope.service()));
        }
        return result;
    }
}
