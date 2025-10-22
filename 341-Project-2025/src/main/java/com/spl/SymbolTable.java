package com.spl;

import java.util.*;

public class SymbolTable {
    // Active scopes (for building phase)
    private final Deque<Map<Integer, Symbol>> scopes = new ArrayDeque<>();
    private final Deque<String> scopeNames = new ArrayDeque<>();
    private final Deque<Map<String, List<Symbol>>> nameMaps = new ArrayDeque<>();

    // All scopes for code generation phase (NEW)
    private final List<Map<Integer, Symbol>> allScopes = new ArrayList<>();
    private final List<String> allScopeNames = new ArrayList<>();
    private final List<Map<String, List<Symbol>>> allNameMaps = new ArrayList<>();

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

        // Also add to all scopes for code generation
        allScopes.add(newScope);
        allScopeNames.add(scopeName);
        allNameMaps.add(newNameMap);
    }

    public void exitScope() {
        scopes.pop();
        nameMaps.pop();
        scopeNames.pop();
        // Note: we DON'T remove from allScopes - they're preserved for code generation
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
        // Search all scopes (including exited ones)
        for (Map<Integer, Symbol> scope : allScopes) {
            if (scope.containsKey(nodeId)) {
                return scope.get(nodeId);
            }
        }
        return null;
    }

    public Symbol lookupVariableInAllScopes(String varName) {
        // Search all name maps (including exited scopes)
        for (Map<String, List<Symbol>> nameMap : allNameMaps) {
            if (nameMap.containsKey(varName)) {
                List<Symbol> symbols = nameMap.get(varName);
                if (!symbols.isEmpty()) {
                    return symbols.get(symbols.size() - 1);
                }
            }
        }
        return null;
    }

    // Rest of your existing methods remain the same...
    public Map<String, List<Symbol>> getNameMapForCurrentScope() {
        return nameMaps.peek();
    }

    public Map<String, List<Symbol>> getGlobalScopeSymbols() {
        if (allNameMaps.isEmpty()) {
            return new HashMap<>();
        }
        // The first element in allNameMaps is the global scope
        return allNameMaps.get(0);
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

    // NEW: Method to get all name maps for code generation
    public List<Map<String, List<Symbol>>> getAllNameMaps() {
        return allNameMaps;
    }

    // NEW: Method to find symbol by name and scope
    public Symbol lookupVariableInScope(String varName, String scopeName) {
        for (int i = 0; i < allScopeNames.size(); i++) {
            if (allScopeNames.get(i).equals(scopeName)) {
                Map<String, List<Symbol>> nameMap = allNameMaps.get(i);
                if (nameMap.containsKey(varName)) {
                    List<Symbol> symbols = nameMap.get(varName);
                    if (!symbols.isEmpty()) {
                        return symbols.get(symbols.size() - 1);
                    }
                }
            }
        }
        return null;
    }
}