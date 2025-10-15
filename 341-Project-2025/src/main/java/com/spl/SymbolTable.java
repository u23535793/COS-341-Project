package com.spl;

import java.util.*;

public class SymbolTable {
    // each scope maps nodeId -> symbol
    private final Deque<Map<Integer, Symbol>> scopes = new ArrayDeque<>();
    private final Deque<String> scopeNames = new ArrayDeque<>();

    // for name-based look up: node names -> list of symbols in current scope
    private final Deque<Map<String, List<Symbol>>> nameMaps = new ArrayDeque<>();

    // all scopes for printing
    private final List<Map<Integer, Symbol>> allScopes = new ArrayList<>();
    private final List<String> allScopeNames = new ArrayList<>();

    // track variable usages (node id -> symbol it refers to)
    private final Map<Integer, Symbol> variableUsages = new HashMap<>();

    public SymbolTable() {
        enterScope("global");
    }

    public void enterScope(String scopeName) {
        Map<Integer, Symbol> newScope = new LinkedHashMap<>();
        Map<String, List<Symbol>> newNameMap = new HashMap<>();
        scopes.push(newScope);
        nameMaps.push(newNameMap);
        scopeNames.push(scopeName);

        allScopes.add(newScope);
        allScopeNames.add(scopeName);
    }

    public void exitScope() {
        scopes.pop();
        nameMaps.pop();
        scopeNames.pop();
    }

    public String currentScopeName() {
        return scopeNames.peek();
    }

    public void define(Symbol sym) {
        scopes.peek().put(sym.nodeId, sym);

        Map<String, List<Symbol>> nameMap = nameMaps.peek();
        nameMap.computeIfAbsent(sym.name, k -> new ArrayList<>()).add(sym);
    }

    public Symbol lookupByNodeId(int nodeId) {
        for (Map<Integer, Symbol> scope : scopes) {
            if (scope.containsKey(nodeId)) {
                return scope.get(nodeId);
            }
        }
        return null;
    }

    public Symbol lookupVariableInAllScopes(String varName) {
        for (Map<String, List<Symbol>> nameMap : nameMaps) {
            if (nameMap.containsKey(varName)) {
                List<Symbol> symbols = nameMap.get(varName);
                if (!symbols.isEmpty()) {
                    return symbols.get(symbols.size() - 1);
                }
            }
        }
        return null;
    }

    public Map<String, List<Symbol>> getNameMapForCurrentScope() {
        return nameMaps.peek();
    }

    public Map<String, List<Symbol>> getGlobalScopeSymbols() {
        if (nameMaps.isEmpty()) {
            return new HashMap<>();
        }
        // The last element in the deque is the global scope
        Deque<Map<String, List<Symbol>>> temp = new ArrayDeque<>(nameMaps);
        while (temp.size() > 1) {
            temp.pop();
        }
        return temp.peek();
    }

    public void recordVariableUsage(int nodeId, Symbol symbol) {
        variableUsages.put(nodeId, symbol);
    }

    public Symbol getVariableUsage(int nodeId) {
        return variableUsages.get(nodeId);
    }

    public void print() {
        for (int i = 0; i < allScopes.size(); i++) {
            Map<Integer, Symbol> scope = allScopes.get(i);
            String scopeName = allScopeNames.get(i);
            System.out.println("Scope " + scopeName + " (level " + (i + 1) + "):");
            for (Symbol s : scope.values()) {
                System.out.println("  " + s);
            }
        }
    }
}