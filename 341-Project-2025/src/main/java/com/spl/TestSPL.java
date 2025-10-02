package com.spl;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class TestSPL {
    public static void main(String[] args) throws Exception {
        String inputFile = args.length > 0 ? args[0] : "test.spl";
        CharStream input = CharStreams.fromFileName(inputFile);

        // lexer
        SPLLexer lexer = new SPLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // parser
        SPLParser parser = new SPLParser(tokens);

        ParseTree tree = parser.spl_prog();

        System.out.println(tree.toStringTree(parser));
    }
}
