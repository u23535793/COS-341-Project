package com.spl;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class SymbolTableBuilder extends SPLBaseVisitor<Void> {
    private final SymbolTable symTable;
    private final SPLParser parser;
    private final Map<ParseTree, Integer> nodeIDs;

    public SymbolTableBuilder(SPLParser parser, Map<ParseTree, Integer> nodeIDs) {
        this.parser = parser;
        this.nodeIDs = nodeIDs;
        this.symTable = new SymbolTable();
    }

    public SymbolTable getSymbolTable() {
        return symTable;
    }

    @Override
    public Void visitVar(SPLParser.VarContext ctx) {
        int id = nodeIDs.get(ctx);
        String name = ctx.getText();
        symTable.define(new Symbol(name, "var", id, symTable.currentScopeName()));
        return null;
    }

    @Override
    public Void visitPdef(SPLParser.PdefContext ctx) {
        int id = nodeIDs.get(ctx);
        String name = ctx.name().getText();
        symTable.define(new Symbol(name, "proc", id, "global"));

        // enter procedure scope
        symTable.enterScope(name);
        if (ctx.param() != null) visit(ctx.param());
        if (ctx.body() != null) visit(ctx.body());
        symTable.exitScope();
        return null;
    }

    @Override
    public Void visitFdef(SPLParser.FdefContext ctx) {
        int id = nodeIDs.get(ctx);
        String name = ctx.name().getText();
        symTable.define(new Symbol(name, "func", id, "global"));

        // enter function scope
        symTable.enterScope(name);
        if (ctx.param() != null) visit(ctx.param());
        if (ctx.body() != null) visit(ctx.body());
        symTable.exitScope();
        return null;
    }

    @Override
    public Void visitParam(SPLParser.ParamContext ctx) {
        if (ctx.maxthree() != null) {
            visitMaxthreeParam(ctx.maxthree());
        }
        return null;
    }

    private Void visitMaxthreeParam(SPLParser.MaxthreeContext ctx) {
        if (ctx.children == null) return null;
        for (ParseTree child : ctx.children) {
            if (child instanceof SPLParser.VarContext) {
                int id = nodeIDs.get(child);
                String name = child.getText();
                symTable.define(new Symbol(name, "param", id, symTable.currentScopeName()));
            }
        }
        return null;
    }

    @Override
    public Void visitBody(SPLParser.BodyContext ctx) {
        SPLParser.MaxthreeContext locals = ctx.maxthree();
        if (locals != null) {
            visitMaxthree(locals);
        }
        visit(ctx.algo());
        return null;
    }

    @Override
    public Void visitMaxthree(SPLParser.MaxthreeContext ctx) {
        if (ctx.children == null) return null;
        for (ParseTree child : ctx.children) {
            if (child instanceof SPLParser.VarContext) {
                int id = nodeIDs.get(child);
                String name = child.getText();
                symTable.define(new Symbol(name, "var", id, symTable.currentScopeName()));
            }
        }
        return null;
    }

    @Override
    public Void visitMainprog(SPLParser.MainprogContext ctx) {
        symTable.enterScope("main");
        if (ctx.variables() != null) visit(ctx.variables());
        if (ctx.algo() != null) visit(ctx.algo());
        symTable.exitScope();
        return null;
    }
}
