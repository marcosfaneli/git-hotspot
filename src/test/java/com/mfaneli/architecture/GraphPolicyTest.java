package com.mfaneli.architecture;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.mfaneli.architecture.ArchitectureGraph.*;

class GraphPolicyTest {
    @Test void detectsMutualCallsButNotOneWayCalls(){
        var nodes=List.of(new Node("a","JAVA_TYPE","A","A.java",1),new Node("b","JAVA_TYPE","B","B.java",1),new Node("am","JAVA_METHOD","a","A.java",2),new Node("bm","JAVA_METHOD","b","B.java",2));
        var edges=new ArrayList<>(List.of(new Edge("a","am","CONTAINS"),new Edge("b","bm","CONTAINS"),new Edge("am","bm","POSSIBLE_CALL")));
        var rule=new GraphPolicy();
        assertTrue(rule.evaluate(new PolicyEngine.Context(List.of(),List.of(),List.of(),new ArchitectureGraph(nodes,edges))).isEmpty());
        edges.add(new Edge("bm","am","POSSIBLE_CALL"));
        assertEquals(1,rule.evaluate(new PolicyEngine.Context(List.of(),List.of(),List.of(),new ArchitectureGraph(nodes,edges))).size());
    }
}
