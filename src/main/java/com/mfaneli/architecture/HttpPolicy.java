package com.mfaneli.architecture;
import java.util.*;
import static com.mfaneli.architecture.PolicyEngine.*;
public record HttpPolicy(String id,String kind,Severity severity) implements Rule {
    public String category(){return "MICROSERVICES";}
    public List<Finding> evaluate(Context c){
        return c.http().stream().filter(n->n.kind().equals(kind)).map(n->new Finding(id,category(),severity,kind.equals("HTTP_CONFIG_UNRESOLVED")?"INCONCLUSIVE":"CANDIDATE",n.file(),n.line(),switch(kind){
            case "HTTP_TIMEOUT_ZERO"->"Zero timeout declared; verify effective client behavior: ";
            case "HTTP_RETRY_DECLARED"->"Retryer declared; review retry safety and idempotency: ";
            default->"Timeout value not statically resolved: ";
        }+n.name())).toList();
    }
}
