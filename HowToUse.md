# SPL Compiler

This is a simple SPL compiler that generates executable BASIC code from SPL source files.

## Directory Structure

Ensure your project has the following structure:

```
Input/
 ├── program1.txt
 └── program2.txt
Compiler.jar
```

* Place all your SPL source files in the `Input` directory.
* The compiler jar (`Compiler.jar`) should be in the same root directory as `Input/`.

## Running the Compiler

Run the compiler using:

```bash
java -jar Compiler.jar
```

You will be presented with a list of available SPL files in the `Input` directory.

* Enter the **number** corresponding to the file you want to compile.

## Output

* The generated BASIC code will be saved in:

```
Output/<InputFileName>/<InputFileName>.txt
```
* The Intermediatecode.html will be saved in:
```
Output/<InputFileName>/<InputFileName>.html
```

For example, compiling `program1.txt` will produce:

```
Output/program1/program1.txt
Output/program1/program1.html
```

* The `Output` directory will be created automatically if it doesn’t exist.

* You can then view the intermediate code in a browser and run the basic code in a basic compiler -> We used https://www.calormen.com/jsbasic/ and bwbasic (linux program) to test the output code.
