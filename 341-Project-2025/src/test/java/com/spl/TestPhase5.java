package com.spl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestPhase5 extends TestCase {

    private static class TestCase {
        String filename;
        boolean expectSuccess;
        String description;
        List<String> expectedPatterns; // Patterns to check in output

        TestCase(String filename, boolean expectSuccess, String description, String... patterns) {
            this.filename = filename;
            this.expectSuccess = expectSuccess;
            this.description = description;
            this.expectedPatterns = Arrays.asList(patterns);
        }
    }

    private static final List<TestCase> TEST_CASES = Arrays.asList(
        new TestCase("test_simple_halt.spl", true, 
            "Valid: Simple halt program",
            "10 STOP"),
        
        new TestCase("test_print_literal.spl", true,
            "Valid: Print literal string",
            "10 t", "20 PRINT", "30 STOP"),
        
        new TestCase("test_simple_assignment.spl", true,
            "Valid: Simple variable assignment",
            "10 t", "20 a" ,"30 STOP"),
        
        new TestCase("test_if_statement.spl", true,
            "Valid: If statement with labels",
            "IF .* THEN \\d+", "\\d+ GOTO \\d+", "\\d+ REM L"),
        
        new TestCase("test_while_loop.spl", true,
            "Valid: While loop with labels",
            "\\d+ REM L", "IF .* THEN \\d+", "\\d+ GOTO \\d+"),
        
        new TestCase("test_do_until.spl", true,
            "Valid: Do-until loop",
            "\\d+ REM L", "IF .* THEN \\d+", "\\d+ GOTO \\d+"),
        
        new TestCase("test_boolean_and.spl", true,
            "Valid: Boolean AND logic",
            "IF .* THEN \\d+", "\\d+ GOTO \\d+"),
        
        new TestCase("test_boolean_or.spl", true,
            "Valid: Boolean OR logic",
            "IF .* THEN \\d+"),
        
        new TestCase("test_not_operator.spl", true,
            "Valid: NOT operator (branch swapping)",
            "IF .* THEN \\d+"),
        
        new TestCase("test_call_with_params.spl", true,
            "Valid: CALL with parameters",
            "\\d+\\s+PRINT\\s+\\w+"),

        
        new TestCase("test_arithmetic_expr.spl", true,
            "Valid: Arithmetic expressions with temps",
            "t\\d+ =", "\\d+ t\\d+"),
        
        new TestCase("test_nested_if.spl", true,
            "Valid: Nested if statements",
            "IF .* THEN \\d+", "\\d+ REM L\\d+"),
        
        new TestCase("test_complex_program.spl", true,
            "Valid: Complex program with all constructs",
            "\\d+ ", "\\d+ REM L", "IF .* THEN \\d+", "\\d+ GOTO \\d+")
    );

    private int passCount = 0;
    private int failCount = 0;
    private List<String> failedTests = new ArrayList<>();

    public TestPhase5(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestPhase5.class);
    }

    public void testAllPhase5Cases() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SPL PHASE 5: BASIC LINE NUMBER GENERATION TEST SUITE");
        System.out.println("=".repeat(80));

        for (TestCase test : TEST_CASES) {
            runSingleTest(test);
        }

        printSummary();
    }

    private void runSingleTest(TestCase test) {
        System.out.println("\n[TEST] " + test.filename);
        System.out.println("       Description: " + test.description);
        System.out.println("       Expected: " + (test.expectSuccess ? "✓ PASS" : "✗ FAIL"));

        try {
            File testFile = new File("tests/phase5/" + test.filename);
            if (!testFile.exists()) {
                // Try phase4 folder as fallback
                testFile = new File("tests/phase4/" + test.filename);
                if (!testFile.exists()) {
                    System.err.println("       Result: ❌ FAILED - File not found: " + testFile.getAbsolutePath());
                    failCount++;
                    failedTests.add(test.filename + " (File not found)");
                    return;
                }
            }

            boolean testPassed = false;
            String basicOutput = "";
            List<String> errors = new ArrayList<>();

            try {
                // Run full pipeline
                CharStream input = CharStreams.fromFileName(testFile.getAbsolutePath());
                SPLLexer lexer = new SPLLexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                SPLParser parser = new SPLParser(tokens);
                ParseTree tree = parser.spl_prog();

                // Assign node IDs
                TestSPL.NodeIDAssigner assigner = new TestSPL.NodeIDAssigner();
                assigner.visit(tree);
                Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();

                // Build symbol table
                SymbolTableBuilder builder = new SymbolTableBuilder(parser, nodeIDs);
                builder.visit(tree);
                SymbolTable symTable = builder.getSymbolTable();

                // Check for semantic violations
                if (!builder.getViolations().isEmpty()) {
                    errors.add("Semantic violations found");
                    testPassed = false;
                } else {
                    // Type analysis
                    TypeAnalyzer typeAnalyzer = new TypeAnalyzer(parser, nodeIDs, symTable);
                    typeAnalyzer.visit(tree);

                    if (!typeAnalyzer.getTypeErrors().isEmpty()) {
                        errors.add("Type errors found");
                        testPassed = false;
                    } else {
                        // Phase 4: Code Generation
                        CodeGenerator codeGen = new CodeGenerator((SPLParser.Spl_progContext)tree, symTable);
                        String intermediateCode = codeGen.generate();

                        // Phase 5: BASIC Line Number Generation
                        BasicCodeGenerator basicGen = new BasicCodeGenerator(intermediateCode);
                        basicOutput = basicGen.generate();

                        // Validate output
                        testPassed = validateBasicOutput(basicOutput, test.expectedPatterns, errors);
                    }
                }

            } catch (Exception e) {
                errors.add("Exception: " + e.getMessage());
                testPassed = false;
            }

            // Report result
            if (testPassed && test.expectSuccess) {
                System.out.println("       Result: ✅ PASSED");
                System.out.println("       Generated " + countLines(basicOutput) + " lines of BASIC code");
                passCount++;
            } else if (!testPassed && !test.expectSuccess) {
                System.out.println("       Result: ✅ PASSED (Expected failure)");
                System.out.println("       Errors: " + String.join(", ", errors));
                passCount++;
            } else {
                System.out.println("       Result: ❌ FAILED");
                if (!errors.isEmpty()) {
                    System.out.println("       Errors:");
                    for (String error : errors) {
                        System.out.println("         - " + error);
                    }
                }
                if (!basicOutput.isEmpty()) {
                    System.out.println("       Generated output:");
                    String[] lines = basicOutput.split("\n");
                    for (int i = 0; i < Math.min(5, lines.length); i++) {
                        System.out.println("         " + lines[i]);
                    }
                    if (lines.length > 5) {
                        System.out.println("         ... (" + (lines.length - 5) + " more lines)");
                    }
                }
                failCount++;
                failedTests.add(test.filename);
            }

        } catch (Exception e) {
            System.out.println("       Result: ❌ FAILED with exception");
            System.out.println("       Exception: " + e.getMessage());
            e.printStackTrace();
            failCount++;
            failedTests.add(test.filename + " (Exception)");
        }
    }

    private boolean validateBasicOutput(String basicOutput, List<String> expectedPatterns, List<String> errors) {
        if (basicOutput == null || basicOutput.trim().isEmpty()) {
            errors.add("Empty BASIC output");
            return false;
        }

        String[] lines = basicOutput.split("\n");
        
        // Check that all lines start with line numbers
        int expectedLineNumber = 10;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            if (!line.matches("^\\d+ .*")) {
                errors.add("Line does not start with line number: " + line);
                return false;
            }
            
            // Extract line number
            String[] parts = line.trim().split(" ", 2);
            try {
                int lineNum = Integer.parseInt(parts[0]);
                if (lineNum != expectedLineNumber) {
                    errors.add("Expected line number " + expectedLineNumber + " but got " + lineNum);
                    return false;
                }
                expectedLineNumber += 10;
            } catch (NumberFormatException e) {
                errors.add("Invalid line number format: " + parts[0]);
                return false;
            }
        }

        // Check for GOTO/THEN label references (should be replaced with line numbers)
        for (String line : lines) {
            if (line.matches(".*GOTO L\\d+.*") || line.matches(".*THEN L\\d+.*")) {
                errors.add("Found unreplaced label reference: " + line);
                return false;
            }
        }

        // Check expected patterns
        for (String pattern : expectedPatterns) {
            boolean found = false;
            Pattern p = Pattern.compile(pattern);
            for (String line : lines) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                errors.add("Expected pattern not found: " + pattern);
                return false;
            }
        }

        return true;
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n").length;
    }

    private void printSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println("Total Tests:    " + TEST_CASES.size());
        System.out.println("Passed:         " + passCount + " ✅");
        System.out.println("Failed:         " + failCount + " ❌");
        System.out.println("Pass Rate:      " + String.format("%.1f%%", (passCount * 100.0) / TEST_CASES.size()));

        if (!failedTests.isEmpty()) {
            System.out.println("\nFailed Tests:");
            for (int i = 0; i < failedTests.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + failedTests.get(i));
            }
        }
        System.out.println("=".repeat(80) + "\n");

        // Assert that all tests passed
        assertTrue("Some tests failed. See output above.", failCount == 0);
    }

    // Additional test methods for specific features

    public void testLineNumbering() {
        String intermediate = "x = 10\nPRINT x\nSTOP";
        BasicCodeGenerator gen = new BasicCodeGenerator(intermediate);
        String basic = gen.generate();
        
        assertTrue("Should start with line 10", basic.startsWith("10 "));
        assertTrue("Should have line 20", basic.contains("\n20 "));
        assertTrue("Should have line 30", basic.contains("\n30 "));
    }

    public void testLabelReplacement() {
        // Create proper intermediate code with labels
        String intermediate = "some_command\nREM L0\nIF V30=V31 THEN L1\nsome_other_command\nGOTO L0\nREM L1\nyet_another_command";
        BasicCodeGenerator gen = new BasicCodeGenerator(intermediate);
        String basic = gen.generate();
        
        // Check that labels are replaced with line numbers
        assertFalse("Should not contain 'THEN L1'", basic.contains("THEN L1"));
        assertFalse("Should not contain 'GOTO L0'", basic.contains("GOTO L0"));
        
        // Check that we have THEN and GOTO with actual line numbers
        Pattern thenPattern = Pattern.compile("THEN \\d+");
        Pattern gotoPattern = Pattern.compile("GOTO \\d+");
        
        assertTrue("Should contain THEN with line number", thenPattern.matcher(basic).find());
        assertTrue("Should contain GOTO with line number", gotoPattern.matcher(basic).find());
    }

    public void testEmptyInput() {
        BasicCodeGenerator gen = new BasicCodeGenerator("");
        String basic = gen.generate();
        assertEquals("Empty input should produce empty output", "", basic);
    }

    public void testNullInput() {
        BasicCodeGenerator gen = new BasicCodeGenerator(null);
        String basic = gen.generate();
        assertEquals("Null input should produce empty output", "", basic);
    }
}