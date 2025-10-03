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

        System.out.println("\nParse Tree (toStringTree format):");
        System.out.println(tree.toStringTree(parser));
        System.out.println();

        // System.out.println("Parse Tree with Node IDs:");
        // printTreeWithIDs(tree, parser, nodeIDs, 0);

        System.out.println("\nNodeID | RuleName  | Text");
        for (ParseTree node : nodeIDs.keySet()) {
            int id = nodeIDs.get(node);
            String ruleName = (node instanceof RuleContext) 
                    ? parser.getRuleNames()[((RuleContext) node).getRuleIndex()]
                    : node.getText();
            System.out.printf("%6d | %-10s | %s%n", id, ruleName, node.getText());
        }

        SymbolTableBuilder builder = new SymbolTableBuilder(parser, nodeIDs);
        builder.visit(tree);
        System.out.println("\n=== Symbol Table ===");
        builder.getSymbolTable().print();

    }

    public static void printTreeWithIDs(ParseTree tree, Parser parser, Map<ParseTree, Integer> nodeIDs, int indent) {
        for (int i = 0; i < indent; i++) System.out.print("  ");

        int id = nodeIDs.get(tree);
        String ruleName = (tree instanceof RuleContext)
                ? parser.getRuleNames()[((RuleContext) tree).getRuleIndex()]
                : tree.getText();

        System.out.println("[" + id + "] " + ruleName + " -> " + tree.getText());

        for (int i = 0; i < tree.getChildCount(); i++) {
            printTreeWithIDs(tree.getChild(i), parser, nodeIDs, indent + 1);
        }
    }
}
