grammar SPL;

// ===============================
// Parser Rules
// ===============================

spl_prog
    : 'glob' '{' variables '}' 
      'proc' '{' procdefs '}' 
      'func' '{' funcdefs '}' 
      'main' '{' mainprog '}' 
      EOF
    ;

variables
    : /* empty */ 
    | var variables
    ;

var
    : UDN
    ;

name
    : UDN
    ;

procdefs
    : /* empty */
    | pdef procdefs
    ;

pdef
    : name '(' param? ')' '{' body '}'
    ;

funcdefs
    : /* empty */
    | fdef funcdefs
    ;

fdef
    : name '(' param? ')' '{' body ';' 'return' atom '}'
    ;

body
    : 'local' '{' maxthree '}' algo
    ;

param
    : maxthree
    ;

maxthree
    : /* empty */
    | var
    | var var
    | var var var
    ;

mainprog
    : 'var' '{' variables '}' algo
    ;

atom
    : var
    | NUMBER
    ;

algo
    : instr
    | instr ';' algo
    ;

instr
    : 'halt'
    | 'print' output
    | name '(' input? ')'      // procedure call
    | assign
    | loop
    | branch
    ;

assign
    : var '=' name '(' input? ')'   // function call
    | var '=' term
    ;

loop
    : 'while' term '{' algo '}'
    | 'do' '{' algo '}' 'until' term
    ;

branch
    : 'if' term '{' algo '}'
    | 'if' term '{' algo '}' 'else' '{' algo '}'
    ;

output
    : atom
    | STRING
    ;

input
    : /* empty */
    | atom
    | atom atom
    | atom atom atom
    ;

term
    : atom
    | '(' unop term ')'
    | '(' term binop term ')'
    ;

unop
    : 'neg'
    | 'not'
    ;

binop
    : 'eq'
    | '>'
    | 'or'
    | 'and'
    | 'plus'
    | 'minus'
    | 'mult'
    | 'div'
    ;

// ===============================
// Lexer Rules
// ===============================

// keywords
GLOB    : 'glob';
PROC    : 'proc';
FUNC    : 'func';
MAIN    : 'main';
VAR_KW  : 'var';
LOCAL   : 'local';
HALT    : 'halt';
PRINT   : 'print';
WHILE   : 'while';
DO      : 'do';
UNTIL   : 'until';
IF      : 'if';
ELSE    : 'else';
RETURN  : 'return';
NEG     : 'neg';
NOT     : 'not';
EQ      : 'eq';
OR      : 'or';
AND     : 'and';
PLUS    : 'plus';
MINUS   : 'minus';
MULT    : 'mult';
DIV     : 'div';
LPAREN : '(' ;
RPAREN : ')' ;
LBRACE : '{' ;
RBRACE : '}' ;
SEMICOLON   : ';' ;
EQ : '=' ;
GT : '>' ;

UDN
    : [a-z][a-z0-9]*
    ;

NUMBER
    : '0' | [1-9][0-9]*
    ;

STRING
    : '"' [a-zA-Z0-9]{0,15} '"'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

COMMENT
    : '//' ~[\r\n]* -> skip
    ;
