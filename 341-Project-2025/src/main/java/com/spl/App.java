package com.spl;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class App {

    public static void main(String[] args) {
        System.out.println("SPL Compiler - Project Type A");
        System.out.println("--------------------------------------------------");

        // === Locate input directory ===
        Path inputDir = Paths.get("Input");
        if (!Files.exists(inputDir)) {
            System.out.println("Input directory not found. Creating one...");
            try {
                Files.createDirectory(inputDir);
            } catch (Exception e) {
                System.out.println("Error creating Input directory: " + e.getMessage());
                return;
            }
        }

        // === Prompt user to select file ===
        String inputFile = selectInputFile(inputDir);
        if (inputFile == null) {
            System.out.println("No file selected. Exiting...");
            return;
        }

        String baseName = new File(inputFile).getName().replaceFirst("[.][^.]+$", "");
        Path outputDir = Paths.get("Output", baseName);

        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            System.out.println("Could not create output directory: " + e.getMessage());
            return;
        }

        System.out.println("Input file: " + inputFile);
        System.out.println("Output directory: " + outputDir);
        System.out.println("--------------------------------------------------");

        try {
            CharStream input = CharStreams.fromFileName(inputFile);
            SPLLexer lexer = new SPLLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // === PHASE 1: LEXICAL ANALYSIS ==================================
            try {
                lexer.removeErrorListeners();
                lexer.addErrorListener(new ThrowingErrorListener());
                tokens.fill();
                System.out.println("Tokens accepted");
            } catch (RuntimeException e) {
                System.out.println("Lexical error: " + e.getMessage());
                return;
            }

            // === PHASE 2: SYNTAX ANALYSIS ===================================
            SPLParser parser = new SPLParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(new ThrowingErrorListener());
            ParseTree tree;

            try {
                tree = parser.spl_prog();
                System.out.println("Syntax accepted");
            } catch (RuntimeException e) {
                System.out.println("Syntax error: " + e.getMessage());
                return;
            }

            // === PHASE 3: NAMING / SYMBOL TABLE ==============================
            NodeIDAssigner assigner = new NodeIDAssigner();
            assigner.visit(tree);
            Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();

            SymbolTableBuilder builder = new SymbolTableBuilder(parser, nodeIDs);
            builder.visit(tree);
            SymbolTable symTable = builder.getSymbolTable();
            List<String> namingErrors = builder.getViolations();

            if (!namingErrors.isEmpty()) {
                System.out.println("Naming error:");
                for (String e : namingErrors) System.out.println("  - " + e);
                return;
            }
            System.out.println("Variable Naming and Function Naming accepted");
            
            // === PHASE 4: TYPE CHECKING =====================================
            TypeAnalyzer typeAnalyzer = new TypeAnalyzer(parser, nodeIDs, symTable);
            typeAnalyzer.visit(tree);
            List<String> typeErrors = typeAnalyzer.getTypeErrors();

            if (!typeErrors.isEmpty()) {
                System.out.println("Type error:");
                for (String e : typeErrors) System.out.println("  - " + e);
                return;
            }
            System.out.println("Types accepted");

            // === PHASE 5: INTERMEDIATE CODE =================================
            CodeGenerator codeGen = new CodeGenerator((SPLParser.Spl_progContext) tree, symTable);
            String intermediateCode = codeGen.generate();
            System.out.println("Intermediate Code accepted");

            String htmlOutput = toHTML(intermediateCode);
            Path htmlFile = outputDir.resolve(baseName + ".html");
            Files.write(htmlFile, htmlOutput.getBytes());
            System.out.println("Intermediate code written to " + htmlFile);

            // === PHASE 6: BASIC EXECUTABLE GENERATION =======================
            BasicCodeGenerator basicGen = new BasicCodeGenerator(intermediateCode);
            String basicCode = basicGen.generate();
            System.out.println("Executable BASIC code generated successfully.");

            Path basFile = outputDir.resolve(baseName + ".bas");
            Files.write(basFile, basicCode.getBytes());
            System.out.println("Executable BASIC code written to " + basFile);

            System.out.println("--------------------------------------------------");
            System.out.println("Compilation successful.");

        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
        }
    }

    // === Prompt user to select file from Input directory ====================
    private static String selectInputFile(Path inputDir) {
        try {
            List<Path> files = Files.list(inputDir)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .collect(Collectors.toList());

            if (files.isEmpty()) {
                System.out.println("No .txt files found in Input/. Please add one.");
                return null;
            }

            System.out.println("Available input files:");
            for (int i = 0; i < files.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + files.get(i).getFileName());
            }

            System.out.print("Select file number to compile: ");
            Scanner scanner = new Scanner(System.in);
            int choice = -1;
            while (choice < 1 || choice > files.size()) {
                System.out.print("> ");
                if (scanner.hasNextInt()) {
                    choice = scanner.nextInt();
                    if (choice < 1 || choice > files.size()) {
                        System.out.println("Invalid selection. Try again.");
                    }
                } else {
                    System.out.println("Please enter a number.");
                    scanner.next(); // consume invalid input
                }
            }

            return files.get(choice - 1).toString();

        } catch (Exception e) {
            System.out.println("Error reading Input directory: " + e.getMessage());
            return null;
        }
    }

    // === Utility for HTML output ============================================
    private static String toHTML(String intermediateCode) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Intermediate Code</title>");
        html.append("<style>body{font-family:monospace;white-space:pre;}</style>");
        html.append("</head><body><h2>Generated Intermediate Code</h2><pre>");
        html.append(intermediateCode.replace("&", "&amp;")
                                    .replace("<", "&lt;")
                                    .replace(">", "&gt;"));
        html.append("</pre></body></html>");
        return html.toString();
    }

    // === Error listener that throws exception ===============================
    private static class ThrowingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e)
                throws RuntimeException {
            throw new RuntimeException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    // === NodeIDAssigner (unchanged) =========================================
    public static class NodeIDAssigner extends SPLBaseVisitor<Integer> {
        private final Map<ParseTree, Integer> nodeIDs = new LinkedHashMap<>();
        private static int counter = 0;
        private static int nextID() { return counter++; }

        public Map<ParseTree, Integer> getNodeIDs() { return nodeIDs; }

        @Override
        public Integer visit(ParseTree tree) {
            if (tree == null) return null;
            int id = nextID();
            nodeIDs.put(tree, id);
            for (int i = 0; i < tree.getChildCount(); i++) visit(tree.getChild(i));
            return id;
        }
    }
}
