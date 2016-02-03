grammar AstralMap;

@header {
package com.error22.smt.remapper.parser;
}

basicName
    :   Identifier
    ;

dottedName
    :   Identifier ('.' Identifier)*
    ;

type
	:	primitiveType
	|	referenceType
	;

primitiveType
	:	'byte'
    |	'short'
    |	'int'
    |	'long'
    |	'char'
    |	'float'
	|	'double'
	|	'boolean'
	;

referenceType
	:	classType
	|	arrayType
	;

classType
	:	dottedName
	;

arrayType
	:	primitiveType dims
	|	classType dims
	;

dims
	:	'[' ']' ('[' ']')*
	;

classDeclaration
    :    oldname=dottedName (ARROW newname=dottedName)? '{' classBody* '}'
    ;

subclassDeclaration
    :    oldname=basicName (ARROW newname=basicName)? '{' classBody* '}'
    ;

classBody
    :   fieldDeclaration
    |   methodDeclaration
    |   subclassDeclaration
    ;

fieldDeclaration
	:	type oldname=basicName (ARROW newname=basicName)? ';'
	;

methodDeclaration
    // Not sure what dims is here for, implies methods in the form of
    // int foobar()[] or int[] foobar()[]
	:	result oldname=basicName (ARROW newname=basicName)? '(' (parameter (',' parameter)*)? ')' ';'
	;

parameter
    : type
    ;

result
	:	type
	|	'void'
	;

// TOP LEVEL RULE
mapFile
    :   classDeclaration*
    ;


ARROW : '->';


// §3.8 Identifiers (must appear after all keywords in the grammar)

Identifier
	:	JavaLetter JavaLetterOrDigit*
	;

fragment
JavaLetter
	:	[a-zA-Z$_] // these are the "java letters" below 0x7F
	|	// covers all characters above 0x7F which are not a surrogate
		~[\u0000-\u007F\uD800-\uDBFF]
		{Character.isJavaIdentifierStart(_input.LA(-1))}?
	|	// covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
		[\uD800-\uDBFF] [\uDC00-\uDFFF]
		{Character.isJavaIdentifierStart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
	;

fragment
JavaLetterOrDigit
	:	[a-zA-Z0-9$_] // these are the "java letters or digits" below 0x7F
	|	// covers all characters above 0x7F which are not a surrogate
		~[\u0000-\u007F\uD800-\uDBFF]
		{Character.isJavaIdentifierPart(_input.LA(-1))}?
	|	// covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
		[\uD800-\uDBFF] [\uDC00-\uDFFF]
		{Character.isJavaIdentifierPart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
	;

//
// Whitespace and comments
//

WS  :  [ \t\r\n\u000C]+ -> skip
    ;

COMMENT
    :   '/*' .*? '*/' -> skip
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> skip
    ;