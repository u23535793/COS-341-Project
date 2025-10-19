package com.spl;

import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class CodeGenerator extends SPLBaseVisitor<String> {
    private final SPLParser.Spl_progContext tree;
    private final SymbolTable symTable;
    private final StringBuilder code = new StringBuilder();
    private int tempCounter = 1;
    private int labelCounter = 1;
    
    // Subtree storage for future inlining phase
    private final Map<String, SPLParser.PdefContext> procedureSubtrees = new HashMap<>();
    private final Map<String, SPLParser.FdefContext> functionSubtrees = new HashMap<>();
    
    public CodeGenerator(SPLParser.Spl_progContext tree, SymbolTable symTable) {
        this.tree = tree;
        this.symTable = symTable;
    }
    
    public String generate() {
        code.setLength(0); // Reset
        tempCounter = 1; // Reset temp counter
        labelCounter = 1; // Reset label counter
        visit(tree);
        return code.toString();
    }
    
    // Helper methods
    private String getInternalName(Symbol sym) {
        // Generate consistent internal names: varName_scope_nodeId
        return sym.name + "_" + sym.scope + "_" + sym.nodeId;
    }
    
    private String newTemp() {
        return "t" + (tempCounter++);
    }
    
    private String newLabel() {
        return "L" + (labelCounter++);
    }
    
    private boolean isNumericOp(String op) {
        return op.equals("+") || op.equals("-") || 
               op.equals("*") || op.equals("/");
    }
    
    private boolean isComparisonOp(String op) {
        return op.equals("=") || op.equals(">");
    }
    
    private boolean isBooleanOp(String op) {
        return op.equals("and") || op.equals("or");
    }
    
    private String generateBooleanExpression(String left, String op, String right) {
        if (op.equals("and")) {
            return generateAndExpression(left, right);
        } else if (op.equals("or")) {
            return generateOrExpression(left, right);
        }
        return "";
    }
    
    private String generateAndExpression(String left, String right) {
        String labelFalse = newLabel();
        String labelTrue = newLabel();
        String labelExit = newLabel();
        
        String temp = newTemp();
        String result = "IF " + left + " THEN " + labelTrue + "\n" +
                       "GOTO " + labelFalse + "\n" +
                       "REM " + labelTrue + "\n" +
                       "IF " + right + " THEN " + labelExit + "\n" +
                       "GOTO " + labelFalse + "\n" +
                       "REM " + labelFalse + "\n" +
                       temp + " = 0\n" +
                       "GOTO " + labelExit + "\n" +
                       "REM " + labelExit + "\n" +
                       temp + " = 1";
        
        code.append(result + "\n");
        return temp;
    }
    
    private String generateOrExpression(String left, String right) {
        String labelTrue = newLabel();
        String labelFalse = newLabel();
        String labelExit = newLabel();
        
        String temp = newTemp();
        String result = "IF " + left + " THEN " + labelTrue + "\n" +
                       "IF " + right + " THEN " + labelTrue + "\n" +
                       "GOTO " + labelFalse + "\n" +
                       "REM " + labelTrue + "\n" +
                       temp + " = 1\n" +
                       "GOTO " + labelExit + "\n" +
                       "REM " + labelFalse + "\n" +
                       temp + " = 0\n" +
                       "REM " + labelExit;
        
        code.append(result + "\n");
        return temp;
    }
    
    // Visitor methods for grammar rules
    
    @Override
    public String visitSpl_prog(SPLParser.Spl_progContext ctx) {
        // Store procedure/function subtrees for future inlining
        if (ctx.procdefs() != null) visit(ctx.procdefs());
        if (ctx.funcdefs() != null) visit(ctx.funcdefs());
        
        // Generate code only from main
        if (ctx.mainprog() != null) {
            String mainCode = visit(ctx.mainprog());
            if (mainCode != null && !mainCode.isEmpty()) {
                code.append(mainCode);
            }
        }
        
        return code.toString();
    }
    
    @Override
    public String visitPdef(SPLParser.PdefContext ctx) {
        // Store subtree for future inlining
        String name = ctx.name().getText();
        procedureSubtrees.put(name, ctx);
        return ""; // No code generated in Phase 4
    }
    
    @Override
    public String visitFdef(SPLParser.FdefContext ctx) {
        // Store subtree for future inlining
        String name = ctx.name().getText();
        functionSubtrees.put(name, ctx);
        return ""; // No code generated in Phase 4
    }
    
    @Override
    public String visitMainprog(SPLParser.MainprogContext ctx) {
        // Skip variables, only translate ALGO
        if (ctx.algo() != null) {
            String algoCode = visit(ctx.algo());
            return algoCode != null ? algoCode : "";
        }
        return "";
    }
    
    @Override
    public String visitAlgo(SPLParser.AlgoContext ctx) {
        if (ctx.algo() == null) {
            if (ctx.instr() != null) {
                String instrCode = visit(ctx.instr());
                return instrCode != null ? instrCode : "";
            }
            return "";
        }
        String instrCode = "";
        if (ctx.instr() != null) {
            instrCode = visit(ctx.instr());
            if (instrCode == null) instrCode = "";
        }
        String algoCode = visit(ctx.algo());
        if (algoCode == null) algoCode = "";
        
        if (!instrCode.isEmpty() && !algoCode.isEmpty()) {
            return instrCode + "\n" + algoCode;
        } else if (!instrCode.isEmpty()) {
            return instrCode;
        } else if (!algoCode.isEmpty()) {
            return algoCode;
        }
        return "";
    }
    
    @Override
    public String visitInstr(SPLParser.InstrContext ctx) {
        if (ctx.HALT() != null) {
            return "STOP";
        } else if (ctx.PRINT() != null && ctx.output() != null) {
            String output = visit(ctx.output());
            return "PRINT " + output;
        } else if (ctx.name() != null && ctx.input() != null) {
            // Procedure call
            String procName = ctx.name().getText();
            String params = visit(ctx.input());
            if (params.isEmpty()) {
                return "CALL " + procName;
            }
            return "CALL " + procName + " " + params;
        } else if (ctx.assign() != null) {
            String assignCode = visit(ctx.assign());
            return assignCode != null ? assignCode : "";
        } else if (ctx.loop() != null) {
            String loopCode = visit(ctx.loop());
            return loopCode != null ? loopCode : "";
        } else if (ctx.branch() != null) {
            String branchCode = visit(ctx.branch());
            return branchCode != null ? branchCode : "";
        }
        return "";
    }
    
    @Override
    public String visitAssign(SPLParser.AssignContext ctx) {
        if (ctx.name() != null && ctx.input() != null) {
            // Function call: VAR = NAME(INPUT)
            String varName = ctx.var().getText();
            Symbol sym = symTable.lookupVariableInAllScopes(varName);
            if (sym != null) {
                String internal = getInternalName(sym);
                String funcName = ctx.name().getText();
                String params = visit(ctx.input());
                if (params.isEmpty()) {
                    return internal + " = CALL " + funcName;
                }
                return internal + " = CALL " + funcName + " " + params;
            }
        } else if (ctx.term() != null) {
            // VAR = TERM
            String varName = ctx.var().getText();
            Symbol sym = symTable.lookupVariableInAllScopes(varName);
            if (sym != null) {
                String internal = getInternalName(sym);
                String termCode = visit(ctx.term());
                return internal + " = " + termCode;
            }
        }
        return "";
    }
    
    @Override
    public String visitBranch(SPLParser.BranchContext ctx) {
        boolean hasNot = false;
        SPLParser.TermContext termCtx = ctx.term();
        
        // Detect (not TERM) pattern
        if (termCtx.unop() != null && termCtx.unop().NOT() != null) {
            hasNot = true;
            termCtx = termCtx.term(0); // Unwrap
        }
        
        String condition = visit(termCtx);
        String labelThen = newLabel();
        String labelExit = newLabel();
        
        // Swap indices if NOT present
        int thenIdx = hasNot ? 1 : 0;
        int elseIdx = hasNot ? 0 : 1;
        
        if (ctx.algo().size() == 2) {
            // if-else
            String elseCode = visit(ctx.algo(elseIdx));
            String thenCode = visit(ctx.algo(thenIdx));
            return "IF " + condition + " THEN " + labelThen + "\n" +
                   (elseCode != null ? elseCode : "") + "\n" +  // Swapped if NOT
                   "GOTO " + labelExit + "\n" +
                   "REM " + labelThen + "\n" +
                   (thenCode != null ? thenCode : "") + "\n" +  // Swapped if NOT
                   "REM " + labelExit + "\n";
        } else {
            // if-only
            String algoCode = visit(ctx.algo(0));
            if (hasNot) {
                // NOT means execute when condition is false
                return "IF " + condition + " THEN " + labelExit + "\n" +
                       (algoCode != null ? algoCode : "") + "\n" +
                       "REM " + labelExit + "\n";
            } else {
                // Normal if-only logic
                return "IF " + condition + " THEN " + labelThen + "\n" +
                       "GOTO " + labelExit + "\n" +
                       "REM " + labelThen + "\n" +
                       (algoCode != null ? algoCode : "") + "\n" +
                       "REM " + labelExit + "\n";
            }
        }
    }
    
    @Override
    public String visitLoop(SPLParser.LoopContext ctx) {
        if (ctx.WHILE() != null) {
            String labelStart = newLabel();
            String labelBody = newLabel();
            String labelExit = newLabel();
            String condition = visit(ctx.term());
            String algoCode = visit(ctx.algo());
            
            return "REM " + labelStart + "\n" +
                   "IF " + condition + " THEN " + labelBody + "\n" +
                   "GOTO " + labelExit + "\n" +
                   "REM " + labelBody + "\n" +
                   (algoCode != null ? algoCode : "") + "\n" +
                   "GOTO " + labelStart + "\n" +
                   "REM " + labelExit + "\n";
        } else if (ctx.DO() != null) {
            // do-until loop
            String labelStart = newLabel();
            String labelExit = newLabel();
            String condition = visit(ctx.term());
            String algoCode = visit(ctx.algo());
            
            return "REM " + labelStart + "\n" +
                   (algoCode != null ? algoCode : "") + "\n" +
                   "IF " + condition + " THEN " + labelExit + "\n" +
                   "GOTO " + labelStart + "\n" +
                   "REM " + labelExit + "\n";
        }
        return "";
    }
    
    @Override
    public String visitOutput(SPLParser.OutputContext ctx) {
        if (ctx.STRING() != null) {
            return ctx.STRING().getText(); // Returns "string"
        }
        if (ctx.atom() != null) {
            String atomResult = visit(ctx.atom());
            return atomResult != null ? atomResult : "";
        }
        return "";
    }
    
    @Override
    public String visitInput(SPLParser.InputContext ctx) {
        if (ctx.atom().isEmpty()) {
            return "";
        }
        List<String> params = new ArrayList<>();
        for (SPLParser.AtomContext atom : ctx.atom()) {
            String atomResult = visit(atom);
            if (atomResult != null) {
                params.add(atomResult);
            }
        }
        return String.join(", ", params);
    }
    
    @Override
    public String visitAtom(SPLParser.AtomContext ctx) {
        if (ctx.NUMBER() != null) {
            return ctx.NUMBER().getText();
        }
        if (ctx.var() != null) {
            // Lookup in symbol table, return internal/renamed identifier
            String varName = ctx.var().getText();
            Symbol sym = symTable.lookupVariableInAllScopes(varName);
            if (sym != null) {
                return getInternalName(sym); // e.g., "x_global_4" or "x_main_79"
            }
        }
        return "";
    }
    
    @Override
    public String visitTerm(SPLParser.TermContext ctx) {
        if (ctx.atom() != null) {
            String atomResult = visit(ctx.atom());
            return atomResult != null ? atomResult : "";
        }
        if (ctx.unop() != null) {
            String op = visit(ctx.unop());
            String term = visit(ctx.term(0));
            if (op.equals("-")) { // neg
                String temp = newTemp();
                code.append(temp + " = -" + term + "\n");
                return temp;
            }
            // 'not' handled in branch context
        }
        if (ctx.binop() != null) {
            String op = visit(ctx.binop());
            String left = visit(ctx.term(0));
            String right = visit(ctx.term(1));
            
            
            if (left == null) left = "";
            if (right == null) right = "";
            
            if (isNumericOp(op)) {
                String temp = newTemp();
                code.append(temp + " = " + left + " " + op + " " + right + "\n");
                return temp;
            }
            if (isComparisonOp(op)) {
                // Return comparison expression for IF statements
                return left + " " + op + " " + right;
            }
            if (isBooleanOp(op)) {
                return generateBooleanExpression(left, op, right);
            }
        }
        return "";
    }
    
    @Override
    public String visitUnop(SPLParser.UnopContext ctx) {
        if (ctx.NEG() != null) {
            return "-";
        } else if (ctx.NOT() != null) {
            return "not";
        }
        return "";
    }
    
    @Override
    public String visitBinop(SPLParser.BinopContext ctx) {
        if (ctx.EQ() != null) return "=";
        if (ctx.GT() != null) return ">";
        if (ctx.PLUS() != null) return "+";
        if (ctx.MINUS() != null) return "-";
        if (ctx.MULT() != null) return "*";
        if (ctx.DIV() != null) return "/";
        if (ctx.OR() != null) return "or";
        if (ctx.AND() != null) return "and";
        return "";
    }
    
    // Variables, parameters, and locals produce no code
    @Override
    public String visitVariables(SPLParser.VariablesContext ctx) {
        return ""; // No translation
    }
    
    @Override
    public String visitVar(SPLParser.VarContext ctx) {
        return ""; // No translation
    }
    
    @Override
    public String visitParam(SPLParser.ParamContext ctx) {
        return ""; // No translation
    }
    
    @Override
    public String visitMaxthree(SPLParser.MaxthreeContext ctx) {
        return ""; // No translation
    }
    
    @Override
    public String visitBody(SPLParser.BodyContext ctx) {
        // Only translate ALGO, skip local variables
        if (ctx.algo() != null) {
            String algoCode = visit(ctx.algo());
            return algoCode != null ? algoCode : "";
        }
        return "";
    }
    
    // Accessor methods for subtrees (for future inlining)
    public Map<String, SPLParser.PdefContext> getProcedureSubtrees() {
        return procedureSubtrees;
    }
    
    public Map<String, SPLParser.FdefContext> getFunctionSubtrees() {
        return functionSubtrees;
    }
}
