package com.spl;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

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
            
            // Phase 4: Code Generation
            System.out.println("\n=== Code Generation (Phase 4) ===");
            try {
                CodeGenerator codeGen = new CodeGenerator((SPLParser.Spl_progContext)tree, symTable);
                String targetCode = codeGen.generate();
                
                System.out.println(targetCode);
                
                // Save intermediate code to .txt file
                String outputFile = inputFile.replace(".spl", ".txt");
                Files.write(Paths.get(outputFile), targetCode.getBytes());
                System.out.println("\nIntermediate code written to: " + outputFile);
                
                // Phase 5: BASIC Line Number Generation
                System.out.println("\n=== BASIC Code Generation (Phase 5) ===");
                BasicCodeGenerator basicGen = new BasicCodeGenerator(targetCode);
                String basicCode = basicGen.generate();
                
                System.out.println(basicCode);
                
                // Phase 5
                String basicOutputFolder = "tests/phase5/bas/";
                Files.createDirectories(Paths.get(basicOutputFolder));

                String fileName = Paths.get(inputFile).getFileName().toString();
                String basicOutputFile = basicOutputFolder + fileName.replace(".spl", ".bas");

                Files.write(Paths.get(basicOutputFile), basicCode.getBytes());
                System.out.println("\nExecutable BASIC code written to: " + basicOutputFile);
                
                // Print label mapping for debugging
                Map<String, Integer> labelMapping = basicGen.getLabelMapping();
                if (!labelMapping.isEmpty()) {
                    System.out.println("\n=== Label to Line Number Mapping ===");
                    for (Map.Entry<String, Integer> entry : labelMapping.entrySet()) {
                        System.out.println(entry.getKey() + " -> Line " + entry.getValue());
                    }
                }
                
            } catch (Exception e) {
                System.out.println("✗ Code generation failed:");
                System.out.println("  - " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("✗ Type errors found:");
            for (String error : typeErrors) {
                System.out.println("  - " + error);
            }
            System.out.println("\nSkipping code generation due to type errors.");
        }
    }
}