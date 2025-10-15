package com.spl;

import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class TypeAnalyzer extends SPLBaseVisitor<String> {
    private final SymbolTable symTable;
    private final SPLParser parser;
    private final Map<ParseTree, Integer> nodeIDs;
    private final List<String> typeErrors = new ArrayList<>();

    // Type constants
    private static final String TYPE_NUMERIC = "numeric";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_COMPARISON = "comparison";
    private static final String TYPE_TYPELESS = "typeless";

    public TypeAnalyzer(SPLParser parser, Map<ParseTree, Integer> nodeIDs, SymbolTable symTable) {
        this.parser = parser;
        this.nodeIDs = nodeIDs;
        this.symTable = symTable;
    }

    public List<String> getTypeErrors() {
        return typeErrors;
    }

    private void addTypeError(String message) {
        typeErrors.add(message);
        System.err.println("[TYPE ERROR] " + message);
    }

    private void addTypeInfo(String message) {
        System.err.println("[TYPE INFO] " + message);
    }

    private boolean isNumericType(String type) {
        return TYPE_NUMERIC.equals(type);
    }

    private boolean isBooleanType(String type) {
        return TYPE_BOOLEAN.equals(type);
    }

    private boolean isComparisonType(String type) {
        return TYPE_COMPARISON.equals(type);
    }

    private boolean isTypeless(String type) {
        return TYPE_TYPELESS.equals(type);
    }

    @Override
    public String visitSpl_prog(SPLParser.Spl_progContext ctx) {
        // Visit all sections
        if (ctx.variables() != null) visit(ctx.variables());
        if (ctx.procdefs() != null) visit(ctx.procdefs());
        if (ctx.funcdefs() != null) visit(ctx.funcdefs());
        if (ctx.mainprog() != null) visit(ctx.mainprog());

        return typeErrors.isEmpty() ? "valid" : "invalid";
    }

    @Override
    public String visitVariables(SPLParser.VariablesContext ctx) {
        // Empty variables are valid
        if (ctx.var() == null) {
            return "valid";
        }

        // Check if var is numeric type
        String varType = visit(ctx.var());
        if (!isNumericType(varType)) {
            addTypeError("Variable must be of type numeric");
            return "invalid";
        }

        // Check remaining variables
        if (ctx.variables() != null) {
            String restType = visit(ctx.variables());
            return "invalid".equals(restType) ? "invalid" : "valid";
        }

        return "valid";
    }

    @Override
    public String visitVar(SPLParser.VarContext ctx) {
        // All variables are of numeric type by fact
        return TYPE_NUMERIC;
    }

    @Override
    public String visitParam(SPLParser.ParamContext ctx) {
        // Parameters are maxthree of vars, all numeric
        if (ctx.maxthree() != null) {
            return visit(ctx.maxthree());
        }
        return "valid";
    }

    @Override
    public String visitMaxthree(SPLParser.MaxthreeContext ctx) {
        // Empty is valid
        if (ctx.var().isEmpty()) {
            return "valid";
        }

        // Check all vars are numeric
        for (SPLParser.VarContext var : ctx.var()) {
            String varType = visit(var);
            if (!isNumericType(varType)) {
                addTypeError("All variables in parameter/local declarations must be numeric");
                return "invalid";
            }
        }

        return "valid";
    }

    @Override
    public String visitPdef(SPLParser.PdefContext ctx) {
        String procName = ctx.name().getText();

        // Check parameter types
        if (ctx.param() != null) {
            String paramType = visit(ctx.param());
            if ("invalid".equals(paramType)) return "invalid";
        }

        // Check body types
        if (ctx.body() != null) {
            String bodyType = visit(ctx.body());
            if ("invalid".equals(bodyType)) return "invalid";
        }

        return "valid";
    }

    @Override
    public String visitFdef(SPLParser.FdefContext ctx) {
        String funcName = ctx.name().getText();

        // Check parameter types
        if (ctx.param() != null) {
            String paramType = visit(ctx.param());
            if ("invalid".equals(paramType)) return "invalid";
        }

        // Check body types
        if (ctx.body() != null) {
            String bodyType = visit(ctx.body());
            if ("invalid".equals(bodyType)) return "invalid";
        }

        // Check return atom is numeric
        if (ctx.atom() != null) {
            String atomType = visit(ctx.atom());
            if (!isNumericType(atomType)) {
                addTypeError("Function '" + funcName + "' must return numeric type, got " + atomType);
                return "invalid";
            }
        }

        return "valid";
    }

    @Override
    public String visitBody(SPLParser.BodyContext ctx) {
        // Check local variables
        if (ctx.maxthree() != null) {
            String localType = visit(ctx.maxthree());
            if ("invalid".equals(localType)) return "invalid";
        }

        // Check algo
        if (ctx.algo() != null) {
            String algoType = visit(ctx.algo());
            if ("invalid".equals(algoType)) return "invalid";
        }

        return "valid";
    }

    @Override
    public String visitMainprog(SPLParser.MainprogContext ctx) {
        // Check variables
        if (ctx.variables() != null) {
            String varType = visit(ctx.variables());
            if ("invalid".equals(varType)) return "invalid";
        }

        // Check algo
        if (ctx.algo() != null) {
            String algoType = visit(ctx.algo());
            if ("invalid".equals(algoType)) return "invalid";
        }

        return "valid";
    }

    @Override
    public String visitAlgo(SPLParser.AlgoContext ctx) {
        // Check instruction
        if (ctx.instr() != null) {
            String instrType = visit(ctx.instr());
            if ("invalid".equals(instrType)) return "invalid";
        }

        // Check remaining algo
        if (ctx.algo() != null) {
            String restType = visit(ctx.algo());
            if ("invalid".equals(restType)) return "invalid";
        }

        return "valid";
    }

    @Override
    public String visitInstr(SPLParser.InstrContext ctx) {
        if (ctx.HALT() != null) {
            return "valid";
        } else if (ctx.PRINT() != null && ctx.output() != null) {
            String outputType = visit(ctx.output());
            return "invalid".equals(outputType) ? "invalid" : "valid";
        } else if (ctx.name() != null && ctx.input() != null) {
            // Procedure call
            String inputType = visit(ctx.input());
            return "invalid".equals(inputType) ? "invalid" : "valid";
        } else if (ctx.assign() != null) {
            String assignType = visit(ctx.assign());
            return assignType;
        } else if (ctx.loop() != null) {
            String loopType = visit(ctx.loop());
            return loopType;
        } else if (ctx.branch() != null) {
            String branchType = visit(ctx.branch());
            return branchType;
        }
        return "valid";
    }

    @Override
    public String visitAssign(SPLParser.AssignContext ctx) {
        // Check variable is numeric
        if (ctx.var() != null) {
            String varType = visit(ctx.var());
            if (!isNumericType(varType)) {
                addTypeError("Assignment target must be numeric type");
                return "invalid";
            }
        }

        // Check either function call or term
        if (ctx.name() != null && ctx.input() != null) {
            // Function call assignment
            String inputType = visit(ctx.input());
            if ("invalid".equals(inputType)) return "invalid";
        } else if (ctx.term() != null) {
            // Term assignment
            String termType = visit(ctx.term());
            if (!isNumericType(termType)) {
                addTypeError("Assignment value must be numeric type, got " + termType);
                return "invalid";
            }
        }

        return "valid";
    }

    @Override
    public String visitLoop(SPLParser.LoopContext ctx) {
        // Check condition is boolean
        if (ctx.term() != null) {
            String termType = visit(ctx.term());
            if (!isBooleanType(termType)) {
                addTypeError("Loop condition must be boolean type, got " + termType);
                return "invalid";
            }
        }

        // Check algo
        if (ctx.algo() != null) {
            String algoType = visit(ctx.algo());
            if ("invalid".equals(algoType)) return "invalid";
        }

        return "valid";
    }

    @Override
    public String visitBranch(SPLParser.BranchContext ctx) {
        // Check condition is boolean
        if (ctx.term() != null) {
            String termType = visit(ctx.term());
            if (!isBooleanType(termType)) {
                addTypeError("Branch condition must be boolean type, got " + termType);
                return "invalid";
            }
        }

        // Check all algo branches
        for (SPLParser.AlgoContext algo : ctx.algo()) {
            String algoType = visit(algo);
            if ("invalid".equals(algoType)) return "invalid";
        }

        return "valid";
    }

    @Override
    public String visitOutput(SPLParser.OutputContext ctx) {
        if (ctx.STRING() != null) {
            // String literals are always valid
            return "valid";
        } else if (ctx.atom() != null) {
            String atomType = visit(ctx.atom());
            if (!isNumericType(atomType)) {
                addTypeError("Output atom must be numeric type, got " + atomType);
                return "invalid";
            }
        }
        return "valid";
    }

    @Override
    public String visitInput(SPLParser.InputContext ctx) {
        // Empty input is valid
        if (ctx.atom().isEmpty()) {
            return "valid";
        }

        // All atoms must be numeric
        for (SPLParser.AtomContext atom : ctx.atom()) {
            String atomType = visit(atom);
            if (!isNumericType(atomType)) {
                addTypeError("All input atoms must be numeric type, got " + atomType);
                return "invalid";
            }
        }

        return "valid";
    }

    @Override
    public String visitAtom(SPLParser.AtomContext ctx) {
        if (ctx.var() != null) {
            // Variable atom - look up type from symbol table
            String varName = ctx.var().getText();
            Symbol symbol = symTable.lookupVariableInAllScopes(varName);
            
            if (symbol != null && ("var".equals(symbol.kind) || "param".equals(symbol.kind))) {
                return TYPE_NUMERIC;
            }
            return TYPE_NUMERIC; // Variables are always numeric
        } else if (ctx.NUMBER() != null) {
            // Number literals are always numeric
            return TYPE_NUMERIC;
        }
        return "invalid";
    }

    @Override
    public String visitTerm(SPLParser.TermContext ctx) {
        if (ctx.atom() != null) {
            // Simple atom term
            String atomType = visit(ctx.atom());
            return atomType;
        } else if (ctx.unop() != null) {
            // Unary operation: (unop term)
            String unopType = visit(ctx.unop());
            List<SPLParser.TermContext> terms = ctx.term();
            
            if (!terms.isEmpty()) {
                String termType = visit(terms.get(0));

                // Check type consistency
                if (isNumericType(unopType)) {
                    if (!isNumericType(termType)) {
                        addTypeError("Numeric operator applied to non-numeric operand");
                        return "invalid";
                    }
                    return TYPE_NUMERIC;
                } else if (isBooleanType(unopType)) {
                    if (!isBooleanType(termType)) {
                        addTypeError("Boolean operator applied to non-boolean operand");
                        return "invalid";
                    }
                    return TYPE_BOOLEAN;
                }
            }
        } else if (ctx.binop() != null) {
            // Binary operation: (term binop term)
            String binopType = visit(ctx.binop());
            List<SPLParser.TermContext> terms = ctx.term();

            if (terms.size() >= 2) {
                String leftType = visit(terms.get(0));
                String rightType = visit(terms.get(1));

                if (isNumericType(binopType)) {
                    if (!isNumericType(leftType) || !isNumericType(rightType)) {
                        addTypeError("Numeric operator requires numeric operands");
                        return "invalid";
                    }
                    return TYPE_NUMERIC;
                } else if (isBooleanType(binopType)) {
                    if (!isBooleanType(leftType) || !isBooleanType(rightType)) {
                        addTypeError("Boolean operator requires boolean operands");
                        return "invalid";
                    }
                    return TYPE_BOOLEAN;
                } else if (isComparisonType(binopType)) {
                    if (!isNumericType(leftType) || !isNumericType(rightType)) {
                        addTypeError("Comparison operator requires numeric operands");
                        return "invalid";
                    }
                    return TYPE_BOOLEAN;
                }
            }
        }
        return "invalid";
    }

    @Override
    public String visitUnop(SPLParser.UnopContext ctx) {
        if (ctx.NEG() != null) {
            return TYPE_NUMERIC;
        } else if (ctx.NOT() != null) {
            return TYPE_BOOLEAN;
        }
        return "invalid";
    }

    @Override
    public String visitBinop(SPLParser.BinopContext ctx) {
        if (ctx.GT() != null || ctx.EQ() != null) {
            return TYPE_COMPARISON;
        } else if (ctx.OR() != null || ctx.AND() != null) {
            return TYPE_BOOLEAN;
        } else if (ctx.PLUS() != null || ctx.MINUS() != null || ctx.MULT() != null || ctx.DIV() != null) {
            return TYPE_NUMERIC;
        }
        return "invalid";
    }
}