package com.spl;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;

public class TestPhase2 extends TestCase {

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
        new TestCase("test_valid_basic.spl", true, "Valid: Basic program with no violations"),
        new TestCase("test_dup_global_vars.spl", false, "Invalid: Duplicate global variables"),
        new TestCase("test_dup_procedures.spl", false, "Invalid: Duplicate procedure declarations"),
        new TestCase("test_dup_functions.spl", false, "Invalid: Duplicate function declarations"),
        new TestCase("test_var_func_conflict.spl", false, "Invalid: Variable conflicts with function name"),
        new TestCase("test_var_proc_conflict.spl", false, "Invalid: Variable conflicts with procedure name"),
        new TestCase("test_func_proc_conflict.spl", false, "Invalid: Function conflicts with procedure name"),
        new TestCase("test_dup_main_vars.spl", false, "Invalid: Duplicate variables in main scope"),
        new TestCase("test_dup_params.spl", false, "Invalid: Duplicate parameter declarations"),
        new TestCase("test_param_shadowing.spl", false, "Invalid: Local variable shadows parameter"),
        new TestCase("test_dup_local_vars.spl", false, "Invalid: Duplicate local variables in function"),
        new TestCase("test_undeclared_var_in_algo.spl", false, "Invalid: Undeclared variable used in procedure algo"),
        new TestCase("test_undeclared_var_in_main.spl", false, "Invalid: Undeclared variable used in main algo"),
        new TestCase("test_var_lookup_local.spl", true, "Valid: Correct local variable usage"),
        new TestCase("test_var_lookup_param.spl", true, "Valid: Correct parameter variable usage"),
        new TestCase("test_var_lookup_global.spl", true, "Valid: Correct global variable usage"),
        new TestCase("test_multiple_violations.spl", false, "Invalid: Multiple violations in one program"),
        new TestCase("test_func_return_param.spl", true, "Valid: Function returns parameter"),
        new TestCase("test_complex_scopes.spl", true, "Valid: Complex but valid scoping"),
        new TestCase("test_dup_local_function.spl", false, "Invalid: Duplicate local variables in function")
    );

    private int passCount = 0;
    private int failCount = 0;
    private List<String> failedTests = new ArrayList<>();

    public TestPhase2(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestPhase2.class);
    }

    public void testAllPhase2Cases() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SPL PHASE 2: NAME-SCOPE ANALYSIS TEST SUITE");
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
            File testFile = new File("tests/Scope/" + test.filename);
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
            List<String> violations = new ArrayList<>();

            try {
                CharStream input = CharStreams.fromFileName(testFile.getAbsolutePath());
                SPLLexer lexer = new SPLLexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                SPLParser parser = new SPLParser(tokens);
                ParseTree tree = parser.spl_prog();

                // Assign node IDs
                TestSPL.NodeIDAssigner assigner = new TestSPL.NodeIDAssigner();
                assigner.visit(tree);
                Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();

                // Build symbol table and validate
                SymbolTableBuilder builder = new SymbolTableBuilder(parser, nodeIDs);
                builder.visit(tree);
                violations = builder.getViolations();

                // Determine pass/fail
                boolean hasViolations = !violations.isEmpty();
                testPassed = (test.expectSuccess && !hasViolations) || (!test.expectSuccess && hasViolations);

            } catch (Exception e) {
                // Parsing errors mean test fails
                testPassed = false;
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }

            // Report result
            if (testPassed) {
                System.out.println("       Result: ✅ PASSED");
                if (!test.expectSuccess) {
                    System.out.println("       Violations found: " + violations.size());
                    for (String v : violations) {
                        System.out.println("         - " + v);
                    }
                }
                passCount++;
            } else {
                System.out.println("       Result: ❌ FAILED");
                System.out.println("       Expected violations: " + (test.expectSuccess ? "0" : "1 or more"));
                System.out.println("       Actual violations: " + violations.size());
                if (!violations.isEmpty()) {
                    for (String v : violations) {
                        System.out.println("         - " + v);
                    }
                } else {
                    System.out.println("       (No violations detected)");
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