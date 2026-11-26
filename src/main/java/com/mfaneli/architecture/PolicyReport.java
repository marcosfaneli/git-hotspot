package com.mfaneli.architecture;
import java.util.*;
import static com.mfaneli.architecture.GraphReport.q;
public final class PolicyReport {
    private PolicyReport(){}
    public static String json(List<PolicyEngine.Finding> findings,int endpoints){
        var items=findings.stream().map(f->"{\"rule\":"+q(f.rule())+",\"category\":"+q(f.category())+",\"severity\":"+q(f.severity().name())+",\"status\":"+q(f.status())+",\"file\":"+q(f.file())+",\"line\":"+f.line()+",\"message\":"+q(f.message())+"}").toList();
        return "{\"schemaVersion\":1,\"endpoints\":"+endpoints+",\"findings\":["+String.join(",",items)+"]}";
    }
}
