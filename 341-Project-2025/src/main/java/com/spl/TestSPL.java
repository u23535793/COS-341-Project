package com.spl;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

public class TestSPL {

    public static class NodeIDGenerator {
        private static int counter = 0;
        public static int nextID() {
            return counter++;
        }
    }

    public static class NodeIDAssigner extends SPLBaseVisitor<Integer> {
        private final Map<ParseTree, Integer> nodeIDs = new LinkedHashMap<>();

        public Map<ParseTree, Integer> getNodeIDs() {
            return nodeIDs;
        }

        @Override
        public Integer visit(ParseTree tree) {
            if (tree == null) return null;

            int id = NodeIDGenerator.nextID();
            nodeIDs.put(tree, id);

            // visit children recursively
            for (int i = 0; i < tree.getChildCount(); i++) {
                visit(tree.getChild(i));
            }

            return id;
        }
    }

    public static void main(String[] args) throws Exception {
        String inputFile = args.length > 0 ? args[0] : "test.spl";
        CharStream input = CharStreams.fromFileName(inputFile);

        SPLLexer lexer = new SPLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        SPLParser parser = new SPLParser(tokens);

        ParseTree tree = parser.spl_prog();

        NodeIDAssigner assigner = new NodeIDAssigner();
        assigner.visit(tree);
        Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();

        System.out.println("\n=== Parse Tree ===");
        System.out.println(tree.toStringTree(parser));
        System.out.println();

        System.out.println("=== NodeID Mapping ===");
        System.out.println("NodeID | RuleName  | Text");
        for (ParseTree node : nodeIDs.keySet()) {
            int id = nodeIDs.get(node);
            String ruleName = (node instanceof RuleContext) 
                    ? parser.getRuleNames()[((RuleContext) node).getRuleIndex()]
                    : node.getText();
            System.out.printf("%6d | %-10s | %s%n", id, ruleName, node.getText());
        }

        // Build symbol table and validate
        SymbolTableBuilder builder = new SymbolTableBuilder(parser, nodeIDs);
        builder.visit(tree);

        System.out.println("\n=== Symbol Table ===");
        SymbolTable symTable = builder.getSymbolTable();
        symTable.print();

        // Print violations
        List<String> violations = builder.getViolations();
        System.out.println("\n=== Semantic Analysis Report ===");
        if (violations.isEmpty()) {
            System.out.println("✓ No violations found!");
        } else {
            System.out.println("✗ Found " + violations.size() + " violation(s):");
            for (int i = 0; i < violations.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + violations.get(i));
            }
        }

        TypeAnalyzer typeAnalyzer = new TypeAnalyzer(parser, nodeIDs, symTable);
        typeAnalyzer.visit(tree);

        List<String> typeErrors = typeAnalyzer.getTypeErrors();
        if (typeErrors.isEmpty()) {
            System.out.println("✓ Program is correctly typed");
        } else {
            System.out.println("✗ Type errors found:");
            for (String error : typeErrors) {
                System.out.println("  - " + error);
            }
        }
    }
}