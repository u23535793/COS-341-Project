# SPL Compiler
## Type A completeness

## Group Members:
- Sean van der Merwe (u22583387)
- Karabelo Taole (u23538318)
- Driya Govender (u23535793)
- Sibusiso Mngomezulu (u20441984)

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

* You can then view the intermediate code in a browser and run the basic code in a basic compiler -> We used [Applesoft Basic](https://www.calormen.com/jsbasic/) ,[C64 Basic emulator](https://stigc.dk/c64/basic/) and bwbasic (linux program) to test the output code.
* The Basic compilers we tested with can only use max 2 char long variables, so we implemented smart temp variable assignment and reuse so it never needs to use something like t10, which will end up being defined as t1 in the compiler. User defined variables are also shortened like that, with scope checks to ensure reuse is safe.
