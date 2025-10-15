package com.spl;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class SymbolTableBuilder extends SPLBaseVisitor<Void> {
    private final SymbolTable symTable;
    private final SPLParser parser;
    private final Map<ParseTree, Integer> nodeIDs;
    private final List<String> violations = new ArrayList<>();

    public SymbolTableBuilder(SPLParser parser, Map<ParseTree, Integer> nodeIDs) {
        this.parser = parser;
        this.nodeIDs = nodeIDs;
        this.symTable = new SymbolTable();
    }

    public SymbolTable getSymbolTable() {
        return symTable;
    }

    public List<String> getViolations() {
        return violations;
    }

    private void addViolation(String message) {
        violations.add(message);
        System.err.println("[VIOLATION] " + message);
    }

    private void addUndeclaredError(String varName, String scope) {
        String message = String.format("Undeclared variable '%s' in scope '%s'", varName, scope);
        violations.add(message);
        System.err.println("[ERROR] " + message);
    }

    private boolean isNameInCurrentScope(String name, String kind) {
        Map<String, List<Symbol>> nameMap = symTable.getNameMapForCurrentScope();
        if (nameMap.containsKey(name)) {
            for (Symbol s : nameMap.get(name)) {
                if (s.scope.equals(symTable.currentScopeName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGlobalFunctionOrProcedure(String name) {
        // Get the global scope symbols
        Map<String, List<Symbol>> globalSymbols = symTable.getGlobalScopeSymbols();
        
        System.err.println("[DEBUG] Checking for duplicate '" + name + "' in global scope");
        System.err.println("[DEBUG] Global symbols available: " + globalSymbols.keySet());
        
        if (globalSymbols.containsKey(name)) {
            for (Symbol s : globalSymbols.get(name)) {
                System.err.println("[DEBUG]   Found symbol: " + s.kind + " '" + s.name + "' in scope '" + s.scope + "'");
                if (("func".equals(s.kind) || "proc".equals(s.kind)) && "global".equals(s.scope)) {
                    System.err.println("[DEBUG] >>> DUPLICATE DETECTED!");
                    return true;
                }
            }
        }
        System.err.println("[DEBUG] No duplicate found");
        return false;
    }

    private Symbol lookupVariableInScope(String varName) {
        return symTable.lookupVariableInAllScopes(varName);
    }

    @Override
    public Void visitSpl_prog(SPLParser.Spl_progContext ctx) {
        // Visit all sections
        if (ctx.variables() != null) visit(ctx.variables());
        if (ctx.procdefs() != null) visitAllProcdefs(ctx.procdefs());
        if (ctx.funcdefs() != null) visitAllFuncdefs(ctx.funcdefs());
        if (ctx.mainprog() != null) visit(ctx.mainprog());

        // Check global scope conflicts: no var with same name as func/proc
        checkGlobalConflicts();

        return null;
    }

    private void visitAllProcdefs(SPLParser.ProcdefsContext ctx) {
        if (ctx == null) return;
        
        // Visit pdef if present
        if (ctx.pdef() != null) {
            visit(ctx.pdef());
        }
        
        // Recursively visit remaining procdefs
        if (ctx.procdefs() != null) {
            visitAllProcdefs(ctx.procdefs());
        }
    }

    private void visitAllFuncdefs(SPLParser.FuncdefsContext ctx) {
        if (ctx == null) {
            return;
        }
        
        // Visit fdef if present
        if (ctx.fdef() != null) {
            visit(ctx.fdef());
        }
        
        // Recursively visit remaining funcdefs
        if (ctx.funcdefs() != null) {
            visitAllFuncdefs(ctx.funcdefs());
        }
    }

    private void checkGlobalConflicts() {
        Map<String, List<Symbol>> globalSymbols = symTable.getGlobalScopeSymbols();

        Set<String> varNames = new HashSet<>();
        Set<String> funcNames = new HashSet<>();
        Set<String> procNames = new HashSet<>();

        for (String name : globalSymbols.keySet()) {
            for (Symbol s : globalSymbols.get(name)) {
                // Only add if it's actually in the global scope
                if (!"global".equals(s.scope)) continue;
                
                System.err.println("[DEBUG GLOBAL] Found " + s.kind + " '" + s.name + "' in scope " + s.scope);
                
                if ("var".equals(s.kind)) varNames.add(name);
                else if ("func".equals(s.kind)) funcNames.add(name);
                else if ("proc".equals(s.kind)) procNames.add(name);
            }
        }

        System.err.println("[DEBUG GLOBAL] Vars: " + varNames + ", Funcs: " + funcNames + ", Procs: " + procNames);

        // Check conflicts - only exact name matches
        for (String var : varNames) {
            if (funcNames.contains(var)) {
                addViolation("Variable '" + var + "' conflicts with function name");
            }
            if (procNames.contains(var)) {
                addViolation("Variable '" + var + "' conflicts with procedure name");
            }
        }

        for (String func : funcNames) {
            if (procNames.contains(func)) {
                addViolation("Function '" + func + "' conflicts with procedure name");
            }
        }
    }

    @Override
    public Void visitVar(SPLParser.VarContext ctx) {
        int id = nodeIDs.get(ctx);
        String name = ctx.getText();
        String currentScope = symTable.currentScopeName();

        // Check for duplicate declaration in current scope
        if (isNameInCurrentScope(name, "var")) {
            addViolation("Duplicate variable '" + name + "' declaration in scope '" + currentScope + "'");
        }

        symTable.define(new Symbol(name, "var", id, currentScope));
        return null;
    }

    @Override
    public Void visitPdef(SPLParser.PdefContext ctx) {
        int id = nodeIDs.get(ctx);
        String name = ctx.name().getText();
        String currentScope = symTable.currentScopeName();

        // Check for duplicate procedure declaration in global scope
        if (isGlobalFunctionOrProcedure(name)) {
            addViolation("Duplicate procedure '" + name + "' declaration in scope '" + currentScope + "'");
        }

        symTable.define(new Symbol(name, "proc", id, currentScope));

        // Enter procedure's local scope
        symTable.enterScope(name);

        // Collect parameters
        Set<String> paramNames = new HashSet<>();
        if (ctx.param() != null) {
            paramNames = visitParamAndCollect(ctx.param());
        }

        // Visit body
        if (ctx.body() != null) {
            visitBodyWithParamTracking(ctx.body(), paramNames);
        }

        symTable.exitScope();
        return null;
    }

    @Override
    public Void visitFdef(SPLParser.FdefContext ctx) {
        int id = nodeIDs.get(ctx);
        String name = ctx.name().getText();
        String currentScope = symTable.currentScopeName();

        // Check for duplicate function declaration in global scope
        if (isGlobalFunctionOrProcedure(name)) {
            addViolation("Duplicate function '" + name + "' declaration in scope '" + currentScope + "'");
        }

        symTable.define(new Symbol(name, "func", id, currentScope));

        // Enter function's local scope
        symTable.enterScope(name);

        // Collect parameters
        Set<String> paramNames = new HashSet<>();
        if (ctx.param() != null) {
            paramNames = visitParamAndCollect(ctx.param());
        }

        // Visit body
        if (ctx.body() != null) {
            visitBodyWithParamTracking(ctx.body(), paramNames);
        }

        symTable.exitScope();
        return null;
    }

    private Set<String> visitParamAndCollect(SPLParser.ParamContext ctx) {
        Set<String> paramNames = new HashSet<>();
        if (ctx.maxthree() != null) {
            if (ctx.maxthree().children != null) {
                for (ParseTree child : ctx.maxthree().children) {
                    if (child instanceof SPLParser.VarContext) {
                        int id = nodeIDs.get(child);
                        String name = child.getText();
                        String currentScope = symTable.currentScopeName();

                        // Check for duplicate parameter
                        if (paramNames.contains(name)) {
                            addViolation("Duplicate parameter '" + name + "' in scope '" + currentScope + "'");
                        }

                        paramNames.add(name);
                        symTable.define(new Symbol(name, "param", id, currentScope));
                    }
                }
            }
        }
        return paramNames;
    }

    private Void visitBodyWithParamTracking(SPLParser.BodyContext ctx, Set<String> paramNames) {
        SPLParser.MaxthreeContext locals = ctx.maxthree();
        if (locals != null) {
            Set<String> localNames = new HashSet<>();
            if (locals.children != null) {
                for (ParseTree child : locals.children) {
                    if (child instanceof SPLParser.VarContext) {
                        int id = nodeIDs.get(child);
                        String name = child.getText();
                        String currentScope = symTable.currentScopeName();

                        // Check for duplicate local variable
                        if (localNames.contains(name)) {
                            addViolation("Duplicate local variable '" + name + "' in scope '" + currentScope + "'");
                        }

                        // Check for parameter shadowing
                        if (paramNames.contains(name)) {
                            addViolation("Local variable '" + name + "' shadows parameter in scope '" + currentScope + "'");
                        }

                        localNames.add(name);
                        symTable.define(new Symbol(name, "var", id, currentScope));
                    }
                }
            }
        }

        // Visit algo (which may contain variable references)
        if (ctx.algo() != null) visit(ctx.algo());
        return null;
    }

    @Override
    public Void visitMainprog(SPLParser.MainprogContext ctx) {
        symTable.enterScope("main");

        // Visit variables in main
        if (ctx.variables() != null) {
            visitVariablesInMain(ctx.variables());
        }

        // Visit algo
        if (ctx.algo() != null) visit(ctx.algo());

        symTable.exitScope();
        return null;
    }

    private void visitVariablesInMain(SPLParser.VariablesContext ctx) {
        Set<String> varNames = new HashSet<>();
        
        // Recursively collect all VAR nodes from variables rule
        collectVariablesRecursively(ctx, varNames);
    }

    private void collectVariablesRecursively(ParseTree tree, Set<String> seenNames) {
        if (tree instanceof SPLParser.VarContext) {
            int id = nodeIDs.get(tree);
            String name = tree.getText();

            // Check for duplicate
            if (seenNames.contains(name)) {
                addViolation("Duplicate variable '" + name + "' in scope '" + symTable.currentScopeName() + "'");
            }
            seenNames.add(name);
            symTable.define(new Symbol(name, "var", id, symTable.currentScopeName()));
        }

        // Recurse through children
        if (tree instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) tree;
            for (int i = 0; i < ctx.getChildCount(); i++) {
                collectVariablesRecursively(ctx.getChild(i), seenNames);
            }
        }
    }

    @Override
    public Void visitAtom(SPLParser.AtomContext ctx) {
        if (ctx.var() != null) {
            String varName = ctx.var().getText();
            Symbol resolved = lookupVariableInScope(varName);

            if (resolved == null) {
                addUndeclaredError(varName, symTable.currentScopeName());
            } else {
                // Update the symbol table to track this reference
                int id = nodeIDs.get(ctx);
                symTable.recordVariableUsage(id, resolved);
            }
        }
        return null;
    }

    @Override
    public Void visitInput(SPLParser.InputContext ctx) {
        // Visit all atoms in input to check for undeclared variables
        if (ctx.children != null) {
            for (ParseTree child : ctx.children) {
                if (child instanceof SPLParser.AtomContext) {
                    visit(child);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitOutput(SPLParser.OutputContext ctx) {
        // Visit atoms in output
        if (ctx.atom() != null) {
            visit(ctx.atom());
        }
        return null;
    }

    @Override
    public Void visitTerm(SPLParser.TermContext ctx) {
        // Visit all atoms in term
        if (ctx.atom() != null) {
            visit(ctx.atom());
        }
        if (ctx.children != null) {
            for (ParseTree child : ctx.children) {
                if (child instanceof SPLParser.AtomContext) {
                    visit(child);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitAssign(SPLParser.AssignContext ctx) {
        // Check the variable being assigned to
        if (ctx.var() != null) {
            String varName = ctx.var().getText();
            Symbol resolved = lookupVariableInScope(varName);
            if (resolved == null) {
                addUndeclaredError(varName, symTable.currentScopeName());
            }
        }

        // Visit the rest (function call or term)
        if (ctx.name() != null && ctx.input() != null) {
            visit(ctx.input());
        } else if (ctx.term() != null) {
            visit(ctx.term());
        }

        return null;
    }
}