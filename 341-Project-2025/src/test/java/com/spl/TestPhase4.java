// package com.spl;

// import junit.framework.Test;
// import junit.framework.TestCase;
// import junit.framework.TestSuite;
// import org.antlr.v4.runtime.*;
// import org.antlr.v4.runtime.tree.*;

// import java.io.*;
// import java.nio.file.*;
// import java.util.*;

// public class TestPhase4 extends TestCase {

//     private static class TestCase {
//         String filename;
//         boolean expectSuccess;
//         String description;
//         String expectedOutputPattern; // Pattern to match in generated code

//         TestCase(String filename, boolean expectSuccess, String description, String expectedOutputPattern) {
//             this.filename = filename;
//             this.expectSuccess = expectSuccess;
//             this.description = description;
//             this.expectedOutputPattern = expectedOutputPattern;
//         }
//     }

//     private static final List<TestCase> TEST_CASES = Arrays.asList(
//         new TestCase("test_codegen_halt.spl", true, "Valid: Basic halt instruction", "STOP"),
//         new TestCase("test_codegen_print_string.spl", true, "Valid: Print string literal", "PRINT \"hello\""),
//         new TestCase("test_codegen_print_number.spl", true, "Valid: Print number", "PRINT 42"),
//         new TestCase("test_codegen_print_var.spl", true, "Valid: Print variable", "PRINT x_global_\\d+"),
//         new TestCase("test_codegen_assign.spl", true, "Valid: Variable assignment", "x_global_\\d+ = 5"),
//         new TestCase("test_codegen_arithmetic.spl", true, "Valid: Arithmetic operations", "t\\d+ = .* \\+ .*"),
//         new TestCase("test_codegen_if.spl", true, "Valid: If statement", "IF .* THEN L\\d+"),
//         new TestCase("test_codegen_if_else.spl", true, "Valid: If-else statement", "IF .* THEN L\\d+.*GOTO L\\d+.*REM L\\d+"),
//         new TestCase("test_codegen_while.spl", true, "Valid: While loop", "REM L\\d+.*IF .* THEN L\\d+.*GOTO L\\d+"),
//         new TestCase("test_codegen_do_until.spl", true, "Valid: Do-until loop", "REM L\\d+.*IF .* THEN L\\d+.*GOTO L\\d+"),
//         new TestCase("test_codegen_procedure_call.spl", true, "Valid: Procedure call", "CALL myproc"),
//         new TestCase("test_codegen_function_call.spl", true, "Valid: Function call", "x_global_\\d+ = CALL myfunc"),
//         new TestCase("test_codegen_comparison.spl", true, "Valid: Comparison operations", "IF .* = .* THEN L\\d+"),
//         new TestCase("test_codegen_complex.spl", true, "Valid: Complex program", ".*")
//     );

//     private int passCount = 0;
//     private int failCount = 0;
//     private List<String> failedTests = new ArrayList<>();

//     public TestPhase4(String testName) {
//         super(testName);
//     }

//     public static Test suite() {
//         return new TestSuite(TestPhase4.class);
//     }

//     public void testAllPhase4Cases() {
//         System.out.println("\n" + "=================================================================================");
//         System.out.println("SPL PHASE 4: CODE GENERATION TEST SUITE");
//         System.out.println("=================================================================================");

//         for (TestCase test : TEST_CASES) {
//             runSingleTest(test);
//         }

//         printSummary();
//     }

//     private void runSingleTest(TestCase test) {
//         System.out.println("\n[TEST] " + test.filename);
//         System.out.println("       Description: " + test.description);
//         System.out.println("       Expected: " + (test.expectSuccess ? "✓ PASS" : "✗ FAIL"));

//         try {
//             File testFile = new File("tests/CodeGen/" + test.filename);
//             if (!testFile.exists()) {
//                 System.err.println("       Result: ❌ FAILED - File not found: " + testFile.getAbsolutePath());
//                 failCount++;
//                 failedTests.add(test.filename + " (File not found)");
//                 return;
//             }

//             // Capture output
//             ByteArrayOutputStream outContent = new ByteArrayOutputStream();
//             PrintStream originalOut = System.out;
//             PrintStream originalErr = System.err;

//             System.setOut(new PrintStream(outContent));
//             System.setErr(new PrintStream(outContent));

//             boolean testPassed = false;
//             String generatedCode = "";

//             try {
//                 // Parse
//                 CharStream input = CharStreams.fromFileName(testFile.getAbsolutePath());
//                 SPLLexer lexer = new SPLLexer(input);
//                 CommonTokenStream tokens = new CommonTokenStream(lexer);
//                 SPLParser parser = new SPLParser(tokens);
//                 ParseTree tree = parser.spl_prog();

//                 // Check for parse errors
//                 if (parser.getNumberOfSyntaxErrors() > 0) {
//                     System.setOut(originalOut);
//                     System.setErr(originalErr);
//                     System.out.println("       Result: ❌ FAILED - Parse errors");
//                     failCount++;
//                     failedTests.add(test.filename + " (Parse error)");
//                     return;
//                 }

//                 // Assign node IDs
//                 TestSPL.NodeIDAssigner assigner = new TestSPL.NodeIDAssigner();
//                 assigner.visit(tree);
//                 Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();

//                 // Build symbol table
//                 SymbolTableBuilder symBuilder = new SymbolTableBuilder(parser, nodeIDs);
//                 symBuilder.visit(tree);

//                 // Check for semantic errors (skip if any)
//                 if (!symBuilder.getViolations().isEmpty()) {
//                     System.setOut(originalOut);
//                     System.setErr(originalErr);
//                     System.out.println("       Result: ❌ FAILED - Semantic errors (should fix before code generation)");
//                     System.out.println("       Semantic violations:");
//                     for (String v : symBuilder.getViolations()) {
//                         System.out.println("         - " + v);
//                     }
//                     failCount++;
//                     failedTests.add(test.filename + " (Semantic errors)");
//                     return;
//                 }

//                 // Run type analysis
//                 TypeAnalyzer typeAnalyzer = new TypeAnalyzer(parser, nodeIDs, symBuilder.getSymbolTable());
//                 typeAnalyzer.visit(tree);

//                 if (!typeAnalyzer.getTypeErrors().isEmpty()) {
//                     System.setOut(originalOut);
//                     System.setErr(originalErr);
//                     System.out.println("       Result: ❌ FAILED - Type errors (should fix before code generation)");
//                     System.out.println("       Type errors:");
//                     for (String e : typeAnalyzer.getTypeErrors()) {
//                         System.out.println("         - " + e);
//                     }
//                     failCount++;
//                     failedTests.add(test.filename + " (Type errors)");
//                     return;
//                 }

//                 // Run code generation
//                 CodeGenerator codeGen = new CodeGenerator((SPLParser.Spl_progContext)tree, symBuilder.getSymbolTable());
//                 generatedCode = codeGen.generate();

//                 // Check if generated code matches expected pattern
//                 boolean matchesPattern = test.expectedOutputPattern.equals(".*") || 
//                                        generatedCode.matches(".*" + test.expectedOutputPattern + ".*");

//                 testPassed = test.expectSuccess && matchesPattern;

//             } catch (Exception e) {
//                 System.setOut(originalOut);
//                 System.setErr(originalErr);
//                 System.out.println("       Result: ❌ FAILED - Exception: " + e.getMessage());
//                 failCount++;
//                 failedTests.add(test.filename + " (Exception)");
//                 return;
//             } finally {
//                 System.setOut(originalOut);
//                 System.setErr(originalErr);
//             }

//             // Report result
//             if (testPassed) {
//                 System.out.println("       Result: ✅ PASSED");
//                 System.out.println("       Generated code:");
//                 System.out.println("         " + generatedCode.replace("\n", "\n         "));
//                 passCount++;
//             } else {
//                 System.out.println("       Result: ❌ FAILED");
//                 System.out.println("       Expected pattern: " + test.expectedOutputPattern);
//                 System.out.println("       Generated code:");
//                 System.out.println("         " + generatedCode.replace("\n", "\n         "));
//                 failCount++;
//                 failedTests.add(test.filename);
//             }

//         } catch (Exception e) {
//             System.out.println("       Result: ❌ FAILED with exception");
//             System.out.println("       Exception: " + e.getMessage());
//             e.printStackTrace();
//             failCount++;
//             failedTests.add(test.filename + " (Exception)");
//         }
//     }

//     private void printSummary() {
//         System.out.println("\n" + "=================================================================================");
//         System.out.println("TEST SUMMARY");
//         System.out.println("=================================================================================");
//         System.out.println("Total Tests:    " + TEST_CASES.size());
//         System.out.println("Passed:         " + passCount + " ✅");
//         System.out.println("Failed:         " + failCount + " ❌");
//         System.out.println("Pass Rate:      " + String.format("%.1f%%", (passCount * 100.0) / TEST_CASES.size()));

//         if (!failedTests.isEmpty()) {
//             System.out.println("\nFailed Tests:");
//             for (int i = 0; i < failedTests.size(); i++) {
//                 System.out.println("  " + (i + 1) + ". " + failedTests.get(i));
//             }
//         }
//         System.out.println("=================================================================================\n");

//         // Assert that all tests passed
//         assertTrue("Some tests failed. See output above.", failCount == 0);
//     }
// }
