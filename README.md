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