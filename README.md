# COS-341-Project-Frontend

>_To build and run the current frontend use: ```cd "341-Project-2025"``` ```mvn -q clean compile exec:java -Dexec.mainClass="com.spl.TestSPL" ```_

This is the frontend of the SPL compiler for the 2025 project. It uses ANTLR 4 for lexical and syntax analysis (parsing) and is built with Maven.  

This README explains what needs to be installed, how to build the project, and how to run the SPL parser on test files.

## Prerequisites 
Make sure the following are installed on your system:  
1. **Java JDK**  
   Check with:  
   ```bash
   java -version
   ```
2. **Maven 3.x**
   Check with:  
   ```bash
   mvn -v
   ```

## Project Structure
```
341-Project-2025/
├── pom.xml                 # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/com/spl/  # Java source code
│   │   │   └── TestSPL.java
│   │   └── antlr4/         # ANTLR grammar files
│   │       └── SPL.g4
│   └── test/               # JUnit test cases (optional)
├── test.spl                # Example SPL source file
```

## Building the Project 
To generate the ANTLR lexer and parser and compile the project, run:  
```
cd "341-Project-2025"
mvn clean compile
```
This will:
- Generate ANTLR parser/lexer code in target/generated-sources/antlr4/.
- Compile all Java source files into target/classes.

## Running the Frontend 
```
String inputFile = args.length > 0 ? args[0] : "test.spl";
CharStream input = CharStreams.fromFileName(inputFile);

SPLLexer lexer = new SPLLexer(input);
CommonTokenStream tokens = new CommonTokenStream(lexer);

SPLParser parser = new SPLParser(tokens);
ParseTree tree = parser.spl_prog();

NodeIDAssigner assigner = new NodeIDAssigner();
assigner.visit(tree);
Map<ParseTree, Integer> nodeIDs = assigner.getNodeIDs();
```
To run the SPL parser on a SPL source file:  
```
mvn exec:java -Dexec.mainClass="com.spl.TestSPL" -Dexec.args="path/to/your/file.spl"
```
- If no argument is provided, it defaults to test.spl in the project root.
- The parser prints the parse tree of the input file to the console.

## Notes 
- All Java source code is in src/main/java/com/spl/.
- ANTLR grammar file is src/main/antlr4/SPL.g4.
  - Any changes to the grammar require a Maven clean and compile to update generated parser/lexer.
- The TestSPL class can be used to test SPL programs quickly.
- To build and run everything at once use: ```mvn -q clean compile exec:java -Dexec.mainClass="com.spl.TestSPL" ```
  - remove ```-q``` to view info and warnings in the console

## Symbol Table 

1. **Scopes** 
- Each scope (global, myproc, myfunc, or main) is represented by a Map<Integer, Symbol> storing symbols defined in that scope.
- Scopes are managed as a stack (Deque) to support nesting:
  - enterScope("scopeName") pushes a new scope onto the stack.
  - exitScope() pops the current scope.
- All scopes are also stored in allScopes for printing and debugging.

2. **Symbols** 
- Each symbol is represented by a Symbol object containing:
  - name — the variable, function, or procedure name.
  - kind — "var", "param", "func", "proc".
  - nodeId — a unique identifier for the parse tree node.
  - scope — the scope in which the symbol is defined.
- Symbols are added to the current scope with define(Symbol sym).

3. **Creating the Symbol Table from the Parse Tree**
```
SymbolTableBuilder builder = new SymbolTableBuilder(parser, nodeIDs);
builder.visit(tree);
System.out.println("\n=== Symbol Table ===");
builder.getSymbolTable().print();
```

## Running Phase 5

### Option 1: Compile and Run a Single SPL File

```bash
# Compile the Java files
mvn clean compile

# Run on a specific .spl file
mvn exec:java -Dexec.mainClass="com.spl.TestSPL" -Dexec.args="tests/phase5/test_print_literal.spl"
```

**Output:**
- Prints the parse tree
- Displays symbol table
- Shows semantic analysis results
- Generates and displays intermediate code (Phase 4)
- Generates and displays BASIC code (Phase 5)
- Saves intermediate code to `.txt` file
- Saves BASIC code to `tests/phase5/bas/*.bas`

### Option 2: Run the Complete Test Suite

```bash
# Run all Phase 5 tests
mvn test -Dtest=TestPhase5
```

**The test suite will:**
- Execute 13 test cases covering different language features
- Validate line numbering
- Verify label resolution
- Check BASIC code structure
- Report pass/fail status for each test
- Display a summary with pass rate

## Test Cases Included

| Test File | Description | Features Tested |
|-----------|-------------|-----------------|
| `test_simple_halt.spl` | Minimal program | Basic STOP statement |
| `test_print_literal.spl` | Print statement | PRINT command |
| `test_simple_assignment.spl` | Variable assignment | Variable initialization |
| `test_if_statement.spl` | Conditional | IF-THEN with labels |
| `test_while_loop.spl` | While loop | Loop with labels and GOTO |
| `test_do_until.spl` | Do-until loop | Loop exit conditions |
| `test_boolean_and.spl` | Boolean AND | Short-circuit evaluation |
| `test_boolean_or.spl` | Boolean OR | Branch logic |
| `test_not_operator.spl` | NOT operator | Branch swapping |
| `test_call_with_params.spl` | Procedure calls | CALL statements |
| `test_arithmetic_expr.spl` | Math expressions | Temporary variables |
| `test_nested_if.spl` | Nested conditionals | Multiple labels |
| `test_complex_program.spl` | All features | Complete program |

## Understanding the Output

### Example Input (test_simple_halt.spl)
```
prog test1 {
  halt;
}
```

### Phase 4 Output (Intermediate Code)
```
STOP
```

### Phase 5 Output (BASIC Code)
```
10 STOP
```

### More Complex Example

**Input:**
```
prog loop_test {
  x := 0;
  while (x < 5) {
    x := x + 1;
  }
  halt;
}
```

**Phase 4 Intermediate:**
```
x_global = 0
REM L0
IF x_global>=5 THEN L1
x_global = x_global+1
GOTO L0
REM L1
STOP
```

**Phase 5 BASIC:**
```
10 x_global = 0
20 REM L0
30 IF x_global>=5 THEN 60
40 x_global = x_global+1
50 GOTO 20
60 REM L1
70 STOP
```

## Key Features

### Line Numbering Strategy
- **Starting Line**: 10
- **Increment**: 10
- **Pattern**: 10, 20, 30, 40, 50, ...

This allows for easy insertion of additional lines later if needed.

### Label Resolution
- Labels in format `L0`, `L1`, `L2`, etc. are tracked
- `GOTO Lx` becomes `GOTO <line_number>`
- `THEN Lx` becomes `THEN <line_number>`
- Label comments (`REM Lx`) remain for readability

### Error Handling
- Empty or null input produces empty output
- Missing labels are preserved (shouldn't occur with valid Phase 4 output)
- Invalid files are reported with clear error messages

## Verification

After running Phase 5, verify the output:

1. **Check line numbers**: Should be sequential (10, 20, 30, ...)
2. **No label references**: No `GOTO Lx` or `THEN Lx` should remain
3. **Valid BASIC syntax**: Each line should follow BASIC conventions
4. **Label mapping**: Console shows which labels map to which line numbers

## Running Generated BASIC Code

The `.bas` files can be run on vintage BASIC interpreters or emulators:

```bash
# Example with a BASIC interpreter
sudo apt install bwbasic
bwbasic tests/phase5/bas/test_simple_halt.bas
```

## Success Criteria

Phase 5 is successful when:
- ✅ All lines have sequential numbers
- ✅ All labels are resolved to line numbers
- ✅ No `GOTO Lx` or `THEN Lx` remain in output
- ✅ Generated BASIC code is syntactically valid
- ✅ All test cases pass

## Next Steps

After Phase 5:
1. Run generated `.bas` files on a BASIC interpreter
2. Test program behavior matches expected output
3. Debug any runtime issues in the source `.spl` files

---

**Phase 5 Complete!** SPL compiler now generates executable BASIC code.