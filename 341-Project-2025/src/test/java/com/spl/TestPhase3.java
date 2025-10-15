package com.spl;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;

public class TestPhase3 extends TestCase {

    private static class TestCase {
        String filename;
        boolean expectSuccess;
        String description;

        TestCase(String filename, boolean expectSuccess, String description) {
            this.filename = filename;
            this.expectSuccess = expectSuccess;
            this.description = description;
        }
    }

    private static final List<TestCase> TEST_CASES = Arrays.asList(
        new TestCase("test_type_valid_basic.spl", true, "Valid: Basic program with no type errors"),
        new TestCase("test_type_valid_operations.spl", true, "Valid: Numeric and comparison operations"),
        new TestCase("test_type_valid_loops.spl", true, "Valid: While and do-until loops with boolean conditions"),
        new TestCase("test_type_return_not_numeric.spl", false, "Invalid: Function return must be numeric"),
        new TestCase("test_type_assign_non_numeric_value.spl", false, "Invalid: Assignment value must be numeric"),
        new TestCase("test_type_loop_condition_not_boolean.spl", false, "Invalid: Loop condition must be boolean"),
        new TestCase("test_type_if_condition_not_boolean.spl", false, "Invalid: If condition must be boolean"),
        new TestCase("test_type_numeric_op_boolean_operand.spl", false, "Invalid: Numeric operator needs numeric operands"),
        new TestCase("test_type_boolean_op_numeric_operand.spl", false, "Invalid: Boolean operator needs boolean operands"),
        new TestCase("test_type_neg_numeric.spl", true, "Valid: Negation is numeric operator"),
        new TestCase("test_type_not_boolean.spl", true, "Valid: Not is boolean operator"),
        new TestCase("test_type_comparison_numeric.spl", true, "Valid: Comparison operators work on numeric"),
        new TestCase("test_type_comparison_on_boolean.spl", false, "Invalid: Comparison needs numeric operands"),
        new TestCase("test_type_output_numeric.spl", true, "Valid: Output accepts numeric atoms"),
        new TestCase("test_type_output_string.spl", true, "Valid: Output accepts string literals"),
        new TestCase("test_type_input_numeric.spl", true, "Valid: Input accepts numeric atoms"),
        new TestCase("test_type_complex_valid.spl", true, "Valid: Complex program with procedures and functions"),
        new TestCase("test_type_multiple_errors.spl", false, "Invalid: Multiple type errors in one program")
    );

    private int passCount = 0;
    private int failCount = 0;
    private List<String> failedTests = new ArrayList<>();

    public TestPhase3(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestPhase3.class);
    }

    public void testAllPhase3Cases() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SPL PHASE 3: TYPE ANALYSIS TEST SUITE");
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
            File testFile = new File("tests/Types/" + test.filename);
            if (!testFile.exists()) {
                System.err.println("       Result: ❌ FAILED - File not found: " + testFile.getAbsolutePath());
                failCount++;
                failedTests.add(test.filename + " (File not found)");
                return;
            }

            // Capture output
            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;

            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(outContent));

            boolean testPassed = false;
            List<String> typeErrors = new ArrayList<>();

            try {
                // Parse
                CharStream input = CharStreams.fromFileName(testFile.getAbsolutePath());
                SPLLexer lexer = new SPLLexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                SPLParser parser = new SPLParser(tokens);
                ParseTree tree = parser.spl_prog();

                // Check for parse errors
                if (parser.getNumberOfSyntaxErrors() > 0) {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                    System.out.println("       Result: ❌ FAILED - Parse errors");
                    failCount++;
                    failedTests.add(test.filename + " (Parse error)");
                    return;
                }

                // Assign node IDs
                TestSPL.NodeIDAssigner assigner = new TestSPL.NodeIDAssigner();
                assigner.visit(tree);
                Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();

                // Build symbol table
                SymbolTableBuilder symBuilder = new SymbolTableBuilder(parser, nodeIDs);
                symBuilder.visit(tree);

                // Check for semantic errors (skip if any)
                if (!symBuilder.getViolations().isEmpty()) {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                    System.out.println("       Result: ❌ FAILED - Semantic errors (should fix before type analysis)");
                    System.out.println("       Semantic violations:");
                    for (String v : symBuilder.getViolations()) {
                        System.out.println("         - " + v);
                    }
                    failCount++;
                    failedTests.add(test.filename + " (Semantic errors)");
                    return;
                }

                // Run type analysis
                TypeAnalyzer typeAnalyzer = new TypeAnalyzer(parser, nodeIDs, symBuilder.getSymbolTable());
                typeAnalyzer.visit(tree);
                typeErrors = typeAnalyzer.getTypeErrors();

                // Determine pass/fail
                boolean hasTypeErrors = !typeErrors.isEmpty();
                testPassed = (test.expectSuccess && !hasTypeErrors) || (!test.expectSuccess && hasTypeErrors);

            } catch (Exception e) {
                System.setOut(originalOut);
                System.setErr(originalErr);
                System.out.println("       Result: ❌ FAILED - Exception: " + e.getMessage());
                failCount++;
                failedTests.add(test.filename + " (Exception)");
                return;
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }

            // Report result
            if (testPassed) {
                System.out.println("       Result: ✅ PASSED");
                if (!test.expectSuccess) {
                    System.out.println("       Type errors found: " + typeErrors.size());
                    for (String e : typeErrors) {
                        System.out.println("         - " + e);
                    }
                }
                passCount++;
            } else {
                System.out.println("       Result: ❌ FAILED");
                System.out.println("       Expected type errors: " + (test.expectSuccess ? "0" : "1 or more"));
                System.out.println("       Actual type errors: " + typeErrors.size());
                if (!typeErrors.isEmpty()) {
                    for (String e : typeErrors) {
                        System.out.println("         - " + e);
                    }
                } else {
                    System.out.println("       (No type errors detected)");
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
}