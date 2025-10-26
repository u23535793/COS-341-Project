package com.spl;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class UninitializedVariableChecker extends SPLBaseVisitor<Void> {
    private final SymbolTable symTable;
    private final SPLParser parser;
    private final Map<ParseTree, Integer> nodeIDs;
    private final List<String> errors = new ArrayList<>();
    
    // Track which variables have been assigned in current scope
    private final Deque<Set<String>> assignedVariablesStack = new ArrayDeque<>();
    private final Deque<String> scopeStack = new ArrayDeque<>();
    
    public UninitializedVariableChecker(SPLParser parser, Map<ParseTree, Integer> nodeIDs, SymbolTable symTable) {
        this.parser = parser;
        this.nodeIDs = nodeIDs;
        this.symTable = symTable;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    private void addError(String message) {
        errors.add(message);
    }
    
    private String currentScope() {
        return scopeStack.isEmpty() ? "global" : scopeStack.peek();
    }
    
    private void enterScope(String scopeName) {
        scopeStack.push(scopeName);
        assignedVariablesStack.push(new HashSet<>());
    }
    
    private void exitScope() {
        scopeStack.pop();
        assignedVariablesStack.pop();
    }
    
    private void markAssigned(String varName) {
        if (!assignedVariablesStack.isEmpty()) {
            // Look up the variable to find which scope it belongs to
            Symbol sym = symTable.lookupVariableLexically(varName, currentScope());
            
            if (sym != null) {
                // Mark as assigned in the scope where the symbol is DEFINED
                String symbolScope = sym.scope;
                
                // Find the scope in the stack and mark it there
                Iterator<String> scopeIterator = scopeStack.descendingIterator();
                Iterator<Set<String>> assignedIterator = assignedVariablesStack.descendingIterator();
                
                while (scopeIterator.hasNext() && assignedIterator.hasNext()) {
                    String scope = scopeIterator.next();
                    Set<String> assigned = assignedIterator.next();
                    
                    if (scope.equals(symbolScope)) {
                        assigned.add(varName);
                        return;
                    }
                }
            }
            
            // Fallback: mark in current scope
            assignedVariablesStack.peek().add(varName);
        }
    }
    
    private boolean isAssigned(String varName, Symbol sym) {
        // For parameters, they're always initialized
        if ("param".equals(sym.kind)) {
            return true;
        }
        
        // For variables, we need to check if they've been assigned
        // We need to look through the scope stack to handle both:
        // 1. Local variables in current scope
        // 2. Variables from outer scopes (global or parent scopes)
        
        // The key is: we check based on WHERE THE SYMBOL WAS DEFINED
        // If the symbol is from the current scope, check current scope's assigned set
        // If the symbol is from a parent scope, check parent scope's assigned set
        
        String symbolScope = sym.scope;
        String currentScopeStr = currentScope();
        
        // If the symbol is defined in the current scope, check current assigned set
        if (symbolScope.equals(currentScopeStr)) {
            if (!assignedVariablesStack.isEmpty()) {
                return assignedVariablesStack.peek().contains(varName);
            }
            return false;
        }
        
        // If the symbol is from a parent scope (e.g., global), 
        // we need to check the parent scope's assigned set
        // We search through the scope stack to find the matching scope
        Iterator<String> scopeIterator = scopeStack.descendingIterator();
        Iterator<Set<String>> assignedIterator = assignedVariablesStack.descendingIterator();
        
        while (scopeIterator.hasNext() && assignedIterator.hasNext()) {
            String scope = scopeIterator.next();
            Set<String> assigned = assignedIterator.next();
            
            if (scope.equals(symbolScope)) {
                return assigned.contains(varName);
            }
        }
        
        return false;
    }
    
    @Override
    public Void visitSpl_prog(SPLParser.Spl_progContext ctx) {
        enterScope("global");
        
        // Visit global variables - they don't need initialization
        if (ctx.variables() != null) {
            visitGlobalVariables(ctx.variables());
        }
        
        // Visit procedure and function definitions
        if (ctx.procdefs() != null) {
            visit(ctx.procdefs());
        }
        if (ctx.funcdefs() != null) {
            visit(ctx.funcdefs());
        }
        
        // Visit main
        if (ctx.mainprog() != null) {
            visit(ctx.mainprog());
        }
        
        exitScope();
        return null;
    }
    
    private void visitGlobalVariables(SPLParser.VariablesContext ctx) {
        // Global variables are DECLARED but NOT automatically initialized
        // They need to be assigned before use, just like local variables
        // So we DON'T mark them as assigned here
        // collectAndMarkVariables(ctx);  // REMOVED - globals aren't auto-initialized
    }
    
    private void collectAndMarkVariables(ParseTree tree) {
        if (tree instanceof SPLParser.VarContext) {
            String varName = tree.getText();
            markAssigned(varName);
        }
        
        if (tree instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) tree;
            for (int i = 0; i < ctx.getChildCount(); i++) {
                collectAndMarkVariables(ctx.getChild(i));
            }
        }
    }
    
    @Override
    public Void visitPdef(SPLParser.PdefContext ctx) {
        String procName = ctx.name().getText();
        
        // Create a NEW isolated scope context for this procedure
        // Save the current global assigned state
        Set<String> savedGlobalAssigned = new HashSet<>(assignedVariablesStack.peek());
        
        enterScope(procName);
        
        // Parameters are initialized when procedure is called
        if (ctx.param() != null && ctx.param().maxthree() != null) {
            for (SPLParser.VarContext var : ctx.param().maxthree().var()) {
                markAssigned(var.getText());
            }
        }
        
        // Local variables are NOT automatically initialized
        // Visit body to check for use before assignment
        if (ctx.body() != null) {
            visit(ctx.body());
        }
        
        exitScope();
        
        // Restore global scope's assigned variables to what it was before
        // This prevents assignments in one procedure from affecting checks in another
        if (!assignedVariablesStack.isEmpty()) {
            assignedVariablesStack.pop();
            assignedVariablesStack.push(savedGlobalAssigned);
        }
        
        return null;
    }
    
    @Override
    public Void visitFdef(SPLParser.FdefContext ctx) {
        String funcName = ctx.name().getText();
        
        // Create a NEW isolated scope context for this function
        // Save the current global assigned state
        Set<String> savedGlobalAssigned = new HashSet<>(assignedVariablesStack.peek());
        
        enterScope(funcName);
        
        // Parameters are initialized when function is called
        if (ctx.param() != null && ctx.param().maxthree() != null) {
            for (SPLParser.VarContext var : ctx.param().maxthree().var()) {
                markAssigned(var.getText());
            }
        }
        
        // Visit body
        if (ctx.body() != null) {
            visit(ctx.body());
        }
        
        // Check return value atom
        if (ctx.atom() != null) {
            checkAtomInitialized(ctx.atom());
        }
        
        exitScope();
        
        // Restore global scope's assigned variables to what it was before
        // This prevents assignments in one function from affecting checks in another
        if (!assignedVariablesStack.isEmpty()) {
            assignedVariablesStack.pop();
            assignedVariablesStack.push(savedGlobalAssigned);
        }
        
        return null;
    }
    
    @Override
    public Void visitBody(SPLParser.BodyContext ctx) {
        // Local variables are declared but NOT initialized
        // Don't mark them as assigned
        
        // Visit algorithm
        if (ctx.algo() != null) {
            visit(ctx.algo());
        }
        
        return null;
    }
    
    @Override
    public Void visitMainprog(SPLParser.MainprogContext ctx) {
        enterScope("main");
        
        // Main variables are NOT automatically initialized
        
        // Visit algorithm
        if (ctx.algo() != null) {
            visit(ctx.algo());
        }
        
        exitScope();
        return null;
    }
    
    @Override
    public Void visitAssign(SPLParser.AssignContext ctx) {
        // First, check the right-hand side (it may reference variables)
        if (ctx.term() != null) {
            visit(ctx.term());
        } else if (ctx.input() != null) {
            visit(ctx.input());
        }
        
        // Then mark the left-hand side as assigned
        if (ctx.var() != null) {
            String varName = ctx.var().getText();
            markAssigned(varName);
        }
        
        return null;
    }
    
    @Override
    public Void visitTerm(SPLParser.TermContext ctx) {
        // Check all atoms in the term
        if (ctx.atom() != null) {
            checkAtomInitialized(ctx.atom());
        }
        
        // Visit children
        if (ctx.term() != null) {
            for (SPLParser.TermContext term : ctx.term()) {
                visit(term);
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitAtom(SPLParser.AtomContext ctx) {
        checkAtomInitialized(ctx);
        return null;
    }
    
    private void checkAtomInitialized(SPLParser.AtomContext ctx) {
        if (ctx.var() != null) {
            String varName = ctx.var().getText();
            
            // Look up the variable to see which scope it belongs to
            Symbol sym = symTable.lookupVariableLexically(varName, currentScope());
            
            if (sym != null) { 
                // Check if it's a parameter (parameters are always initialized)
                if ("param".equals(sym.kind)) {
                    return;
                }
                
                if (!isAssigned(varName, sym)) {
                    addError(String.format(
                        "Variable '%s' may not have been initialized in scope '%s'",
                        varName, currentScope()
                    ));
                }
            }
        }
    }
    
    @Override
    public Void visitOutput(SPLParser.OutputContext ctx) {
        if (ctx.atom() != null) {
            checkAtomInitialized(ctx.atom());
        }
        return null;
    }
    
    @Override
    public Void visitInput(SPLParser.InputContext ctx) {
        for (SPLParser.AtomContext atom : ctx.atom()) {
            checkAtomInitialized(atom);
        }
        return null;
    }
    
    @Override
    public Void visitBranch(SPLParser.BranchContext ctx) {
        // Check condition
        if (ctx.term() != null) {
            visit(ctx.term());
        }
        
        // For branches, we need to be conservative
        // Create copies of assigned variables for each branch
        Set<String> beforeBranch = new HashSet<>(assignedVariablesStack.peek());
        
        Set<String> thenAssigned = new HashSet<>(beforeBranch);
        Set<String> elseAssigned = new HashSet<>(beforeBranch);
        
        // Visit then branch
        if (ctx.algo().size() > 0) {
            assignedVariablesStack.pop();
            assignedVariablesStack.push(thenAssigned);
            visit(ctx.algo(0));
            thenAssigned = new HashSet<>(assignedVariablesStack.peek());
        }
        
        // Visit else branch if exists
        if (ctx.algo().size() > 1) {
            assignedVariablesStack.pop();
            assignedVariablesStack.push(elseAssigned);
            visit(ctx.algo(1));
            elseAssigned = new HashSet<>(assignedVariablesStack.peek());
        }
        
        // After branch: only variables assigned in BOTH branches are considered assigned
        Set<String> afterBranch = new HashSet<>(beforeBranch);
        if (ctx.algo().size() > 1) {
            // Only add variables assigned in both branches
            for (String var : thenAssigned) {
                if (elseAssigned.contains(var) && !beforeBranch.contains(var)) {
                    afterBranch.add(var);
                }
            }
        }
        // If no else branch, we can't guarantee any new assignments
        
        assignedVariablesStack.pop();
        assignedVariablesStack.push(afterBranch);
        
        return null;
    }
    
    @Override
    public Void visitLoop(SPLParser.LoopContext ctx) {
        // Check condition
        if (ctx.term() != null) {
            visit(ctx.term());
        }
        
        // For loops, we can't guarantee the body executes
        // So we don't propagate assignments from the loop body
        Set<String> beforeLoop = new HashSet<>(assignedVariablesStack.peek());
        
        // Visit body
        if (ctx.algo() != null) {
            visit(ctx.algo());
        }
        
        // Restore assigned variables to state before loop
        assignedVariablesStack.pop();
        assignedVariablesStack.push(beforeLoop);
        
        return null;
    }
}