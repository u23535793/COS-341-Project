package com.spl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class CodeGenerator extends SPLBaseVisitor<String> {
    private final SPLParser.Spl_progContext tree;
    private final SymbolTable symTable;
    private final StringBuilder code = new StringBuilder();
    private int tempCounter = 1;
    private char tempPrefix = 't';
    private int labelCounter = 1;
    
    private final Stack<String> availableTemps = new Stack<>();
    private final List<String> allTemps = new ArrayList<>();
    
    private final Map<String, String> variableNameMap = new HashMap<>();
    private final java.util.Set<String> usedNames = new java.util.HashSet<>();
    
    private String currentScope = "global";
    
    private final Map<String, SPLParser.PdefContext> procedureSubtrees = new HashMap<>();
    private final Map<String, SPLParser.FdefContext> functionSubtrees = new HashMap<>();
    
    private StringBuilder termCode = new StringBuilder();
    
    public CodeGenerator(SPLParser.Spl_progContext tree, SymbolTable symTable) {
        this.tree = tree;
        this.symTable = symTable;
    }
    
    public String generate() {
        code.setLength(0);
        tempCounter = 1;
        tempPrefix = 't';
        labelCounter = 1;
        currentScope = "global";
        availableTemps.clear();
        allTemps.clear();
        variableNameMap.clear();
        usedNames.clear();
        
        for (char c = 't'; c <= 'z'; c++) {
            for (int i = 1; i <= 9; i++) {
                usedNames.add(String.valueOf(c) + i);
            }
        }
        
        visit(tree);
        return code.toString();
    }
    
    private String getInternalName(Symbol sym) {
        String original = sym.name + sym.nodeId;
        
        if (variableNameMap.containsKey(original)) {
            return variableNameMap.get(original);
        }
        
        String shortened = generateShortenedName(sym.name);
        variableNameMap.put(original, shortened);
        usedNames.add(shortened);
        
        return shortened;
    }
    
    private String generateShortenedName(String originalName) {
        char firstChar = Character.toLowerCase(originalName.charAt(0));
        
        if (firstChar >= 't' && firstChar <= 'z') {
            firstChar = 'a';
        }
        
        for (int i = 1; i <= 9; i++) {
            String candidate = String.valueOf(firstChar) + i;
            if (!usedNames.contains(candidate)) {
                return candidate;
            }
        }
        for (char c = 'a'; c <= 's'; c++) {
            for (int i = 1; i <= 9; i++) {
                String candidate = String.valueOf(c) + i;
                if (!usedNames.contains(candidate)) {
                    return candidate;
                }
            }
        }
        
        throw new RuntimeException("Exhausted all possible variable names for: " + originalName);
    }
    
    private String getInternalName(String varName) {
        Symbol sym = symTable.lookupVariableLexically(varName, currentScope);
        if (sym != null) {
            return getInternalName(sym);
        }
        return varName;
    }
    
    private String newTemp() {
        if (!availableTemps.isEmpty()) {
            return availableTemps.pop();
        }
        
        if (tempCounter > 9) {
            tempCounter = 1;
            tempPrefix++;
            if (tempPrefix > 'z') {
                throw new RuntimeException("Exhausted all temporary variable names");
            }
        }
        
        String temp = tempPrefix + String.valueOf(tempCounter);
        tempCounter++;
        allTemps.add(temp);
        return temp;
    }
    
    private void releaseTemp(String temp) {
        // Only release if it's actually a temp variable we created
        if (allTemps.contains(temp) && !availableTemps.contains(temp)) {
            availableTemps.push(temp);
        }
    }
    
    private String newLabel() {
        return "L" + (labelCounter++);
    }
    
    @Override
    public String visitSpl_prog(SPLParser.Spl_progContext ctx) {
        if (ctx.procdefs() != null) {
            visit(ctx.procdefs());
        }
        if (ctx.funcdefs() != null) {
            visit(ctx.funcdefs());
        }
        
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
        String name = ctx.name().getText();
        procedureSubtrees.put(name, ctx);
        return "";
    }
    
    @Override
    public String visitFdef(SPLParser.FdefContext ctx) {
        String name = ctx.name().getText();
        functionSubtrees.put(name, ctx);
        return "";
    }
    
    @Override
    public String visitMainprog(SPLParser.MainprogContext ctx) {
        String previousScope = currentScope;
        currentScope = "main";

        String result = "";
        if (ctx.algo() != null) {
            String algoCode = visit(ctx.algo());
            result = algoCode != null ? algoCode : "";
        }

        currentScope = previousScope;
        return result;
    }
    
    @Override
    public String visitAlgo(SPLParser.AlgoContext ctx) {
        StringBuilder algoCode = new StringBuilder();
        
        if (ctx.instr() != null) {
            String instrCode = visit(ctx.instr());
            if (instrCode != null && !instrCode.isEmpty()) {
                algoCode.append(instrCode).append("\n");
            }
        }
        
        if (ctx.algo() != null) {
            String nestedAlgoCode = visit(ctx.algo());
            if (nestedAlgoCode != null && !nestedAlgoCode.isEmpty()) {
                algoCode.append(nestedAlgoCode);
            }
        }
        
        return algoCode.toString().trim();
    }
    
    @Override
    public String visitInstr(SPLParser.InstrContext ctx) {
        if (ctx.HALT() != null) {
            return "STOP";
                } else if (ctx.PRINT() != null && ctx.output() != null) {
                    StringBuilder printCode = new StringBuilder();
                    termCode.setLength(0);

                    String output = visit(ctx.output());

                    // If output is a string literal, BASIC cannot assign strings to numeric temps.
                    // Print the string literal directly instead of creating a temp variable.
                    if (output != null && output.startsWith("\"") && output.endsWith("\"")) {
                            return "PRINT " + output;
                    }

                    String temp = newTemp();
                    printCode.append(temp).append(" = ").append(output).append("\n");
                    printCode.append("PRINT ").append(temp);
          
                    releaseTemp(temp);

                    return printCode.toString();
        } else if (ctx.name() != null && ctx.input() != null) {
            String procName = ctx.name().getText();
            SPLParser.PdefContext pdef = procedureSubtrees.get(procName);
            if (pdef == null) {
                throw new RuntimeException("Undefined procedure: " + procName);
            }
            return inlineProcedureCall(pdef, ctx.input());
        } else if (ctx.assign() != null) {
            return visit(ctx.assign());
        } else if (ctx.loop() != null) {
            return visit(ctx.loop());
        } else if (ctx.branch() != null) {
            return visit(ctx.branch());
        }
        return "";
    }
    
    @Override
    public String visitAssign(SPLParser.AssignContext ctx) {
        if (ctx.name() != null && ctx.input() != null) {
            String varName = ctx.var().getText();
            String internalVar = getInternalName(varName);
            String funcName = ctx.name().getText();
            SPLParser.FdefContext fdef = functionSubtrees.get(funcName);
            if (fdef == null) {
                throw new RuntimeException("Undefined function: " + funcName);
            }
            return inlineFunctionCall(fdef, ctx.input(), internalVar);
        } else if (ctx.term() != null) {
            String varName = ctx.var().getText();
            String internalVar = getInternalName(varName);
            
            termCode.setLength(0);
            String resultTemp = visitTermForValue(ctx.term());
            
            StringBuilder assignCode = new StringBuilder();
            if (termCode.length() > 0) {
                assignCode.append(termCode);
            }
            assignCode.append(internalVar).append(" = ").append(resultTemp);
            
            // Don't release the result temp if it's used in assignment
            
            return assignCode.toString();
        }
        return "";
    }
    
    @Override
    public String visitBranch(SPLParser.BranchContext ctx) {
        boolean hasNot = false;
        SPLParser.TermContext termCtx = ctx.term();
        
        if (termCtx.unop() != null && termCtx.unop().NOT() != null) {
            hasNot = true;
            termCtx = termCtx.term(0);
        }
        
        termCode.setLength(0);
        String condition = visitTermForCondition(termCtx);
        String termCodeStr = termCode.toString();
        
        String labelThen = newLabel();
        String labelExit = newLabel();
        
        int thenIdx = hasNot ? 1 : 0;
        int elseIdx = hasNot ? 0 : 1;
        
        StringBuilder branchCode = new StringBuilder();
        if (termCodeStr.length() > 0) {
            branchCode.append(termCodeStr);
        }
        
        if (ctx.algo().size() == 2) {
            String elseCode = visit(ctx.algo(elseIdx));
            String thenCode = visit(ctx.algo(thenIdx));
            
            branchCode.append("IF ").append(condition).append(" THEN ").append(labelThen).append("\n");
            if (elseCode != null && !elseCode.isEmpty()) {
                branchCode.append(elseCode).append("\n");
            }
            branchCode.append("GOTO ").append(labelExit).append("\n");
            branchCode.append("REM ").append(labelThen).append("\n");
            if (thenCode != null && !thenCode.isEmpty()) {
                branchCode.append(thenCode).append("\n");
            }
            branchCode.append("REM ").append(labelExit);
            
            return branchCode.toString();
        } else {
            String algoCode = visit(ctx.algo(0));
            
            if (hasNot) {
                branchCode.append("IF ").append(condition).append(" THEN ").append(labelExit).append("\n");
                if (algoCode != null && !algoCode.isEmpty()) {
                    branchCode.append(algoCode).append("\n");
                }
                branchCode.append("REM ").append(labelExit);
            } else {
                branchCode.append("IF ").append(condition).append(" THEN ").append(labelThen).append("\n");
                branchCode.append("GOTO ").append(labelExit).append("\n");
                branchCode.append("REM ").append(labelThen).append("\n");
                if (algoCode != null && !algoCode.isEmpty()) {
                    branchCode.append(algoCode).append("\n");
                }
                branchCode.append("REM ").append(labelExit);
            }
            
            return branchCode.toString();
        }
    }
    
    @Override
    public String visitLoop(SPLParser.LoopContext ctx) {
        if (ctx.WHILE() != null) {
            String labelStart = newLabel();
            String labelBody = newLabel();
            String labelExit = newLabel();
            
            termCode.setLength(0);
            String condition = visitTermForCondition(ctx.term());
            String termCodeStr = termCode.toString();
            
            String algoCode = visit(ctx.algo());
            
            StringBuilder loopCode = new StringBuilder();
            loopCode.append("REM ").append(labelStart).append("\n");
            if (termCodeStr.length() > 0) {
                loopCode.append(termCodeStr);
            }
            loopCode.append("IF ").append(condition).append(" THEN ").append(labelBody).append("\n");
            loopCode.append("GOTO ").append(labelExit).append("\n");
            loopCode.append("REM ").append(labelBody).append("\n");
            if (algoCode != null && !algoCode.isEmpty()) {
                loopCode.append(algoCode).append("\n");
            }
            loopCode.append("GOTO ").append(labelStart).append("\n");
            loopCode.append("REM ").append(labelExit);
            
            return loopCode.toString();
        } else if (ctx.DO() != null) {
            String labelStart = newLabel();
            String labelExit = newLabel();
            
            termCode.setLength(0);
            String condition = visitTermForCondition(ctx.term());
            String termCodeStr = termCode.toString();
            
            String algoCode = visit(ctx.algo());
            
            StringBuilder loopCode = new StringBuilder();
            loopCode.append("REM ").append(labelStart).append("\n");
            if (algoCode != null && !algoCode.isEmpty()) {
                loopCode.append(algoCode).append("\n");
            }
            if (termCodeStr.length() > 0) {
                loopCode.append(termCodeStr);
            }
            loopCode.append("IF ").append(condition).append(" THEN ").append(labelExit).append("\n");
            loopCode.append("GOTO ").append(labelStart).append("\n");
            loopCode.append("REM ").append(labelExit);
            
            return loopCode.toString();
        }
        return "";
    }
    
    private String visitTermForCondition(SPLParser.TermContext ctx) {
        if (ctx.atom() != null) {
            String atomValue = visit(ctx.atom());
            String temp = newTemp();
            termCode.append(temp).append(" = ").append(atomValue).append("\n");
            return temp;
        }
        
        if (ctx.unop() != null) {
            String op = visit(ctx.unop());
            String innerTemp = visitTermForCondition(ctx.term(0));
            
            if (op.equals("-")) {
                String temp = newTemp();
                termCode.append(temp).append(" = -").append(innerTemp).append("\n");
                releaseTemp(innerTemp);
                return temp;
            } else if (op.equals("not")) {
                String labelTrue = newLabel();
                String labelEnd = newLabel();
                String temp = newTemp();
                termCode.append(temp).append(" = 0\n");
                termCode.append("IF ").append(innerTemp).append(" THEN ").append(labelTrue).append("\n");
                termCode.append(temp).append(" = 1\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelTrue).append("\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(innerTemp);
                return temp;
            }
        }
        
        if (ctx.binop() != null && ctx.term().size() == 2) {
            String op = visit(ctx.binop());
            
            String leftTemp = visitTermForCondition(ctx.term(0));
            String rightTemp = visitTermForCondition(ctx.term(1));
            
            if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
                String temp = newTemp();
                termCode.append(temp).append(" = ").append(leftTemp).append(" ").append(op).append(" ").append(rightTemp).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
            
            // For comparisons in conditions, return the comparison directly
            // Note: We keep temps alive for use in the IF statement
            if (op.equals("=") || op.equals(">")) {
                return leftTemp + " " + op + " " + rightTemp;
            }
            
            if (op.equals("and")) {
                String labelCheckB = newLabel();
                String labelTrue = newLabel();
                String labelEnd = newLabel();
                
                String temp = newTemp();
                termCode.append(temp).append(" = 0\n");
                termCode.append("IF ").append(leftTemp).append(" THEN ").append(labelCheckB).append("\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelCheckB).append("\n");
                termCode.append("IF ").append(rightTemp).append(" THEN ").append(labelTrue).append("\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelTrue).append("\n");
                termCode.append(temp).append(" = 1\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
            
            if (op.equals("or")) {
                String labelEnd = newLabel();
                
                String temp = newTemp();
                termCode.append(temp).append(" = 1\n");
                termCode.append("IF ").append(leftTemp).append(" THEN ").append(labelEnd).append("\n");
                termCode.append("IF ").append(rightTemp).append(" THEN ").append(labelEnd).append("\n");
                termCode.append(temp).append(" = 0\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
        }
        
        return "0";
    }
    
    private String visitTermForValue(SPLParser.TermContext ctx) {
        if (ctx.atom() != null) {
            String atomValue = visit(ctx.atom());
            String temp = newTemp();
            termCode.append(temp).append(" = ").append(atomValue).append("\n");
            return temp;
        }
        
        if (ctx.unop() != null) {
            String op = visit(ctx.unop());
            String innerTemp = visitTermForValue(ctx.term(0));
            
            if (op.equals("-")) {
                String temp = newTemp();
                termCode.append(temp).append(" = -").append(innerTemp).append("\n");
                releaseTemp(innerTemp);
                return temp;
            } else if (op.equals("not")) {
                String labelTrue = newLabel();
                String labelEnd = newLabel();
                String temp = newTemp();
                termCode.append(temp).append(" = 0\n");
                termCode.append("IF ").append(innerTemp).append(" THEN ").append(labelTrue).append("\n");
                termCode.append(temp).append(" = 1\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelTrue).append("\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(innerTemp);
                return temp;
            }
        }
        
        if (ctx.binop() != null && ctx.term().size() == 2) {
            String op = visit(ctx.binop());
            
            String leftTemp = visitTermForValue(ctx.term(0));
            String rightTemp = visitTermForValue(ctx.term(1));
            
            if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
                String temp = newTemp();
                termCode.append(temp).append(" = ").append(leftTemp).append(" ").append(op).append(" ").append(rightTemp).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
            
            // For comparisons in assignments, evaluate to boolean temp
            if (op.equals("=") || op.equals(">")) {
                String labelTrue = newLabel();
                String labelEnd = newLabel();
                String temp = newTemp();
                termCode.append(temp).append(" = 0\n");
                termCode.append("IF ").append(leftTemp).append(" ").append(op).append(" ").append(rightTemp).append(" THEN ").append(labelTrue).append("\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelTrue).append("\n");
                termCode.append(temp).append(" = 1\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
            
            if (op.equals("and")) {
                String labelCheckB = newLabel();
                String labelTrue = newLabel();
                String labelEnd = newLabel();
                
                String temp = newTemp();
                termCode.append(temp).append(" = 0\n");
                termCode.append("IF ").append(leftTemp).append(" THEN ").append(labelCheckB).append("\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelCheckB).append("\n");
                termCode.append("IF ").append(rightTemp).append(" THEN ").append(labelTrue).append("\n");
                termCode.append("GOTO ").append(labelEnd).append("\n");
                termCode.append("REM ").append(labelTrue).append("\n");
                termCode.append(temp).append(" = 1\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
            
            if (op.equals("or")) {
                String labelEnd = newLabel();
                
                String temp = newTemp();
                termCode.append(temp).append(" = 1\n");
                termCode.append("IF ").append(leftTemp).append(" THEN ").append(labelEnd).append("\n");
                termCode.append("IF ").append(rightTemp).append(" THEN ").append(labelEnd).append("\n");
                termCode.append(temp).append(" = 0\n");
                termCode.append("REM ").append(labelEnd).append("\n");
                releaseTemp(leftTemp);
                releaseTemp(rightTemp);
                return temp;
            }
        }
        
        return "0";
    }
    
    @Override
    public String visitOutput(SPLParser.OutputContext ctx) {
        if (ctx.STRING() != null) {
            return ctx.STRING().getText();
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
            if (atomResult != null && !atomResult.isEmpty()) {
                params.add(atomResult);
            }
        }
        return String.join(" ", params);
    }
    
    @Override
    public String visitAtom(SPLParser.AtomContext ctx) {
        if (ctx.NUMBER() != null) {
            return ctx.NUMBER().getText();
        }
        if (ctx.var() != null) {
            String varName = ctx.var().getText();
            Symbol sym = symTable.lookupVariableLexically(varName, currentScope);
            if (sym != null) {
                return getInternalName(sym);
            }
            return varName;
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
    
    @Override
    public String visitVariables(SPLParser.VariablesContext ctx) {
        return "";
    }
    
    @Override
    public String visitVar(SPLParser.VarContext ctx) {
        return "";
    }
    
    @Override
    public String visitParam(SPLParser.ParamContext ctx) {
        return "";
    }
    
    @Override
    public String visitMaxthree(SPLParser.MaxthreeContext ctx) {
        return "";
    }
    
    @Override
    public String visitBody(SPLParser.BodyContext ctx) {
        if (ctx.algo() != null) {
            String algoCode = visit(ctx.algo());
            return algoCode != null ? algoCode : "";
        }
        return "";
    }

    private String inlineFunctionCall(SPLParser.FdefContext fdef, SPLParser.InputContext input, String resultVar) {
        StringBuilder inlineCode = new StringBuilder();
        
        String previousScope = currentScope;
        
        // IMPORTANT: Evaluate actual parameters in the CALLING scope (before changing scope)
        List<String> actualParams = new ArrayList<>();
        if (input != null && input.atom() != null) {
            for (SPLParser.AtomContext atom : input.atom()) {
                actualParams.add(visit(atom));
            }
        }
        
        // NOW change to the function's scope
        currentScope = fdef.name().getText();
        
        List<String> formalInternalNames = new ArrayList<>();
        if (fdef.param() != null && fdef.param().maxthree() != null) {
            SPLParser.MaxthreeContext maxthree = fdef.param().maxthree();
            if (maxthree.var() != null) {
                for (SPLParser.VarContext varCtx : maxthree.var()) {
                    String formalParam = varCtx.getText();
                    // formal parameters live in the function's scope
                    Symbol formalSym = symTable.lookupVariableInScope(formalParam, currentScope);
                    if (formalSym != null) {
                        String internalName = getInternalName(formalSym);
                        formalInternalNames.add(internalName);
                    }
                }
            }
        }
        
        Map<String, String> paramMap = new HashMap<>();
        for (int i = 0; i < formalInternalNames.size() && i < actualParams.size(); i++) {
            paramMap.put(formalInternalNames.get(i), actualParams.get(i));
        }
        
        if (fdef.body() != null && fdef.body().algo() != null) {
            String bodyCode = visit(fdef.body().algo());
            
            if (!bodyCode.isEmpty()) {
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    bodyCode = bodyCode.replace(entry.getKey(), entry.getValue());
                }
                inlineCode.append(bodyCode).append("\n");
            }
        }
        
        if (fdef.atom() != null) {
            String returnValue = visit(fdef.atom());
            
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                returnValue = returnValue.replace(entry.getKey(), entry.getValue());
            }
            
            inlineCode.append(resultVar).append(" = ").append(returnValue);
        } else {
            inlineCode.append(resultVar).append(" = 0");
        }
        
        currentScope = previousScope;
        
        return inlineCode.toString();
    }

    private String inlineProcedureCall(SPLParser.PdefContext pdef, SPLParser.InputContext input) {
        StringBuilder inlineCode = new StringBuilder();
        
        String previousScope = currentScope;
        
        // IMPORTANT: Evaluate actual parameters in the CALLING scope (before changing scope)
        List<String> actualParams = new ArrayList<>();
        if (input != null && input.atom() != null) {
            for (SPLParser.AtomContext atom : input.atom()) {
                actualParams.add(visit(atom));
            }
        }
        
        // NOW change to the procedure's scope
        currentScope = pdef.name().getText();
        
        List<String> formalInternalNames = new ArrayList<>();
        if (pdef.param() != null && pdef.param().maxthree() != null) {
            SPLParser.MaxthreeContext maxthree = pdef.param().maxthree();
            if (maxthree.var() != null) {
                for (SPLParser.VarContext varCtx : maxthree.var()) {
                    String formalParam = varCtx.getText();
                    // formal parameters live in the procedure's scope
                    Symbol formalSym = symTable.lookupVariableInScope(formalParam, currentScope);
                    if (formalSym != null) {
                        String internalName = getInternalName(formalSym);
                        formalInternalNames.add(internalName);
                    }
                }
            }
        }
        
        Map<String, String> paramMap = new HashMap<>();
        for (int i = 0; i < formalInternalNames.size() && i < actualParams.size(); i++) {
            paramMap.put(formalInternalNames.get(i), actualParams.get(i));
        }
        
        if (pdef.body() != null && pdef.body().algo() != null) {
            String bodyCode = visit(pdef.body().algo());
            
            if (!bodyCode.isEmpty()) {
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    bodyCode = bodyCode.replace(entry.getKey(), entry.getValue());
                }
                inlineCode.append(bodyCode);
            }
        }
        
        currentScope = previousScope;
        
        return inlineCode.toString();
    }
    
    public Map<String, SPLParser.PdefContext> getProcedureSubtrees() {
        return procedureSubtrees;
    }
    
    public Map<String, SPLParser.FdefContext> getFunctionSubtrees() {
        return functionSubtrees;
    }

    @Override
    public String visitProcdefs(SPLParser.ProcdefsContext ctx) {
        if (ctx.pdef() != null) {
            visit(ctx.pdef());
        }
        if (ctx.procdefs() != null) {
            visit(ctx.procdefs());
        }
        return "";
    }

    @Override
    public String visitFuncdefs(SPLParser.FuncdefsContext ctx) {
        if (ctx.fdef() != null) {
            visit(ctx.fdef());
        }
        if (ctx.funcdefs() != null) {
            visit(ctx.funcdefs());
        }
        return "";
    }
}