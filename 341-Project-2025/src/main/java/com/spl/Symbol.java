package com.spl;

public class Symbol {
    public String name;
    public String kind;   // "var", "func", "proc", "param"
    public int nodeId;  
    public String scope;  // "global", "main", "myfunc"

    public Symbol(String name, String kind, int nodeId, String scope) {
        this.name = name;
        this.kind = kind;
        this.nodeId = nodeId;
        this.scope = scope;
    }

    @Override
    public String toString() {
        return String.format("Symbol(name=%s, kind=%s, scope=%s, nodeId=%d)",
                name, kind, scope, nodeId);
    }
}
