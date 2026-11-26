package com.mfaneli.architecture;
import java.util.*;
import java.util.regex.Pattern;
import static com.mfaneli.architecture.PolicyEngine.*;

public record RoutePolicy(boolean conflicts) implements Rule {
    public String id(){return conflicts?"REST-005":"REST-004";}
    public String category(){return "REST";}
    public Severity severity(){return Severity.WARNING;}
    public List<Finding> evaluate(Context c){
        var result=new ArrayList<Finding>();
        var routes=new TreeMap<String,com.mfaneli.discovery.RestInventory.Endpoint>();
        for(var e:c.endpoints()){
            if(e.path().equals("<unresolved>")||e.path().contains("$")||e.path().contains("#")||e.path().contains(":")||e.path().contains("*")) continue;
            if(conflicts){
                if(e.conditional()||e.verb().equals("UNKNOWN")) continue;
                var key=e.verb()+" "+e.path().replaceAll("\\{[^{}]+\\}","{}");
                var previous=routes.putIfAbsent(key,e);
                if(previous!=null&&!previous.equals(e)) result.add(new Finding(id(),category(),severity(),"CANDIDATE",e.file(),e.line(),"Potential duplicate route "+key+" also at "+previous.file()+":"+previous.line()+"; verify application scope"));
            } else {
                var matcher=Pattern.compile("\\{([^{}]+)\\}").matcher(e.path());
                var names=new HashSet<String>();
                while(matcher.find()) names.add(matcher.group(1));
                for(var name:new TreeSet<>(e.variables())) if(!name.equals("<unresolved>")&&!names.contains(name))
                    result.add(new Finding(id(),category(),severity(),"CANDIDATE",e.file(),e.line(),"PathVariable "+name+" absent from "+e.path()));
            }
        }
        return result;
    }
}
